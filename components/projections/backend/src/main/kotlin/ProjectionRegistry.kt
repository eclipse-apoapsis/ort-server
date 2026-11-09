/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.eclipse.apoapsis.ortserver.components.projections

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

import org.eclipse.apoapsis.ortserver.dao.transaction

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.compoundOr
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.update

/**
 * The registry that stores the state of all projections in the database.
 *
 * Besides tracking the status of projections, the registry also serves as the work queue for building them: Builds
 * are claimed by updating the status atomically and are protected by a lease. If the owner of a lease does not renew
 * it in time, for example because the process crashed, the build can be claimed by another process. All operations
 * that change the result of a build are fenced by the lease owner, so that a process that lost its lease cannot
 * overwrite the result of another process.
 *
 * The [clock] is only called within transactions. Outside of tests, it returns the time of the database, so that
 * leases do not depend on the clocks of the processes that compete for builds.
 */
class ProjectionRegistry internal constructor(
    private val db: Database,
    private val clock: Clock
) {
    constructor(db: Database) : this(db, DatabaseClock)

    /** Return the state of the projection with the given [key], or `null` if it was never requested. */
    suspend fun get(key: ProjectionKey): ProjectionState? = db.transaction {
        ProjectionRegistryTable.selectAll()
            .where { key.matches() }
            .singleOrNull()
            ?.toProjectionState()
    }

    /**
     * Request a build of the projection with the given [key] for the given [schemaVersion]. A build is requested if
     * the projection does not exist yet, if it was requested for an older schema version, or if its last build failed
     * and the retry delay has passed. Return `true` if a build was requested, or `false` if the projection is
     * already up to date, being built, or was requested for a newer schema version.
     *
     * Schema versions only move forward, so that instances running different versions of a builder, for example
     * during a rolling upgrade, do not repeatedly replace each other's projections. Requesting a newer schema version
     * also replaces a running build for an older version.
     */
    suspend fun requestBuild(key: ProjectionKey, schemaVersion: Int): Boolean = db.transaction {
        val now = clock.now()

        val inserted = ProjectionRegistryTable.insertIgnore {
            it[ortRunId] = key.ortRunId
            it[projectionType] = key.type
            it[this.schemaVersion] = schemaVersion
            it[status] = ProjectionStatus.PENDING
            it[attempts] = 0
            it[createdAt] = now
            it[lastAccessedAt] = now
        }.insertedCount > 0

        if (inserted) return@transaction true

        // Use a lock that does not conflict with the key share locks that running builds hold on this row through the
        // foreign keys of the projection tables, so that requests do not wait for running builds to finish.
        val state = ProjectionRegistryTable.selectAll()
            .where { key.matches() }
            .forUpdate(ForUpdateOption.PostgreSQL.ForNoKeyUpdate)
            .single()
            .toProjectionState()

        val isOutdated = state.schemaVersion < schemaVersion
        val isRetryable = state.schemaVersion == schemaVersion && state.status == ProjectionStatus.FAILED &&
                state.nextAttempt?.let { it <= now } != false

        if (!isOutdated && !isRetryable) return@transaction false

        ProjectionRegistryTable.update({ key.matches() }) {
            it[this.schemaVersion] = schemaVersion
            it[status] = ProjectionStatus.PENDING
            it[attempts] = 0
            it.clearBuildState()
        } > 0
    }

    /**
     * Claim the build of the projection with the given [key] for the given [schemaVersion] and [owner] with a lease of
     * the given [leaseDuration]. This succeeds only if the build was requested for this schema version, and if it is
     * pending or the lease of a previous owner has expired. Return `true` if the build was claimed.
     */
    suspend fun claim(key: ProjectionKey, schemaVersion: Int, owner: String, leaseDuration: Duration): Boolean =
        db.transaction {
            val now = clock.now()
            val isRequestedVersion = ProjectionRegistryTable.schemaVersion eq schemaVersion

            ProjectionRegistryTable.update({ key.matches() and isRequestedVersion and isClaimable(now) }) {
                it.setBuilding(owner, now + leaseDuration)
            } > 0
        }

    /**
     * Claim the build of the oldest projection that is pending, whose lease has expired, or whose failed build can be
     * retried. Builds that have already been attempted [maxAttempts] times are not claimed, so that a build that
     * repeatedly crashes the instances running it is not retried forever. Only builds that were requested for one of
     * the given [schemaVersions], which map projection types to schema versions, are claimed, so that instances
     * running different versions of a builder, for example during a rolling upgrade, do not claim each other's
     * builds. Use the given [owner] and [leaseDuration] for the lease. Return the key of the claimed projection, or
     * `null` if there is nothing to build. Rows locked by other transactions are skipped, so that concurrent callers
     * never claim the same build.
     */
    suspend fun claimNext(
        schemaVersions: Map<String, Int>,
        owner: String,
        leaseDuration: Duration,
        maxAttempts: Int
    ): ProjectionKey? = db.transaction {
        if (schemaVersions.isEmpty()) return@transaction null

        val now = clock.now()

        val isRequestedVersion = schemaVersions.map { (type, schemaVersion) ->
            (ProjectionRegistryTable.projectionType eq type) and
                    (ProjectionRegistryTable.schemaVersion eq schemaVersion)
        }.compoundOr()

        val isRetryable = (ProjectionRegistryTable.status eq ProjectionStatus.FAILED) and
                (ProjectionRegistryTable.nextAttempt lessEq now)

        val hasAttemptsLeft = ProjectionRegistryTable.attempts less maxAttempts

        val key = ProjectionRegistryTable
            .select(ProjectionRegistryTable.ortRunId, ProjectionRegistryTable.projectionType)
            .where { isRequestedVersion and (isClaimable(now) or isRetryable) and hasAttemptsLeft }
            .orderBy(ProjectionRegistryTable.createdAt)
            .limit(1)
            // As in requestBuild(), do not conflict with the key share locks of running builds, so that builds whose
            // lease has expired can be claimed even if their transaction is still open.
            .forUpdate(ForUpdateOption.PostgreSQL.ForNoKeyUpdate(ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED))
            .singleOrNull()
            ?.let { ProjectionKey(it[ProjectionRegistryTable.ortRunId], it[ProjectionRegistryTable.projectionType]) }
            ?: return@transaction null

        ProjectionRegistryTable.update({ key.matches() }) {
            it.setBuilding(owner, now + leaseDuration)
        }

        key
    }

    /**
     * Extend the lease of the given [owner] on the build of the projection with the given [key] by the given
     * [leaseDuration]. Return `false` if the lease was lost in the meantime.
     */
    suspend fun heartbeat(key: ProjectionKey, owner: String, leaseDuration: Duration): Boolean = db.transaction {
        ProjectionRegistryTable.update({ key.matches() and isBuildingBy(owner) }) {
            it[leaseUntil] = clock.now() + leaseDuration
        } > 0
    }

    /**
     * Mark the projection with the given [key] as ready for the given [schemaVersion], if its build is still owned by
     * the given [owner]. Return `false` if the lease was lost in the meantime, in which case the caller must roll back
     * the data it has written for the projection.
     */
    suspend fun markReady(key: ProjectionKey, owner: String, schemaVersion: Int): Boolean = db.transaction {
        ProjectionRegistryTable.update({ key.matches() and isBuildingBy(owner) }) {
            it[this.schemaVersion] = schemaVersion
            it[status] = ProjectionStatus.READY
            it[attempts] = 0
            it.clearBuildState()
        } > 0
    }

    /**
     * Mark the build of the projection with the given [key] as failed with the given [error], if it is still owned by
     * the given [owner]. The build can be retried after the given [retryDelay]. Return `false` if the lease was lost
     * in the meantime.
     */
    suspend fun markFailed(key: ProjectionKey, owner: String, error: String, retryDelay: Duration): Boolean =
        db.transaction {
            ProjectionRegistryTable.update({ key.matches() and isBuildingBy(owner) }) {
                it[status] = ProjectionStatus.FAILED
                it[leaseOwner] = null
                it[leaseUntil] = null
                it[nextAttempt] = clock.now() + retryDelay
                it[this.error] = error
            } > 0
        }

    /**
     * Mark builds whose lease has expired after they have been attempted [maxAttempts] times as failed, because they
     * were abandoned repeatedly, for example because the build crashed the instances running it. Like other failed
     * builds, they are retried on the next request. Return the number of failed builds.
     */
    suspend fun failAbandonedBuilds(maxAttempts: Int): Int = db.transaction {
        val now = clock.now()

        ProjectionRegistryTable.update({
            (ProjectionRegistryTable.status eq ProjectionStatus.BUILDING) and
                    (ProjectionRegistryTable.leaseUntil less now) and
                    (ProjectionRegistryTable.attempts greaterEq maxAttempts)
        }) {
            it[status] = ProjectionStatus.FAILED
            it[leaseOwner] = null
            it[leaseUntil] = null
            it[nextAttempt] = now
            it[error] = "The build was abandoned $maxAttempts times, for example because it crashed the instances " +
                    "running it."
        }
    }

    /**
     * Release all builds owned by the given [owner], so that other processes can claim them immediately. The
     * interrupted attempts are not counted. Return the number of released builds.
     */
    suspend fun release(owner: String): Int = db.transaction {
        ProjectionRegistryTable.update({ isBuildingBy(owner) }) {
            it[status] = ProjectionStatus.PENDING
            it[attempts] = ProjectionRegistryTable.attempts - 1
            it[leaseOwner] = null
            it[leaseUntil] = null
        }
    }

    /**
     * Record an access to the projection with the given [key]. To avoid a write on every access, the time of the last
     * access is only updated if it is older than the given [minInterval].
     */
    suspend fun touch(key: ProjectionKey, minInterval: Duration) {
        db.transaction {
            val now = clock.now()

            ProjectionRegistryTable.update({
                key.matches() and (ProjectionRegistryTable.lastAccessedAt less (now - minInterval))
            }) {
                it[lastAccessedAt] = now
            }
        }
    }

    /**
     * Delete all projections that have not been accessed since the given [threshold], together with their data.
     * Projections that are currently being built are kept, unless their lease has expired, so that builds that were
     * abandoned and are not claimed anymore, for example because no instance builds their schema version anymore, do
     * not remain forever. Return the number of deleted projections.
     */
    suspend fun deleteNotAccessedSince(threshold: Instant): Int = db.transaction {
        val now = clock.now()

        ProjectionRegistryTable.deleteWhere {
            (lastAccessedAt less threshold) and ((status neq ProjectionStatus.BUILDING) or (leaseUntil less now))
        }
    }

    private fun ProjectionKey.matches(): Op<Boolean> =
        (ProjectionRegistryTable.ortRunId eq ortRunId) and (ProjectionRegistryTable.projectionType eq type)

    /** A build is claimable if it is pending, or if it is being built but the lease has expired. */
    private fun isClaimable(now: Instant): Op<Boolean> {
        val isLeaseExpired = (ProjectionRegistryTable.status eq ProjectionStatus.BUILDING) and
                (ProjectionRegistryTable.leaseUntil less now)

        return (ProjectionRegistryTable.status eq ProjectionStatus.PENDING) or isLeaseExpired
    }

    private fun isBuildingBy(owner: String): Op<Boolean> =
        (ProjectionRegistryTable.status eq ProjectionStatus.BUILDING) and
                (ProjectionRegistryTable.leaseOwner eq owner)

    private fun UpdateBuilder<*>.setBuilding(owner: String, leaseUntil: Instant) {
        this[ProjectionRegistryTable.status] = ProjectionStatus.BUILDING
        this[ProjectionRegistryTable.leaseOwner] = owner
        this[ProjectionRegistryTable.leaseUntil] = leaseUntil
        this[ProjectionRegistryTable.attempts] = ProjectionRegistryTable.attempts + 1
    }

    private fun UpdateBuilder<*>.clearBuildState() {
        this[ProjectionRegistryTable.leaseOwner] = null
        this[ProjectionRegistryTable.leaseUntil] = null
        this[ProjectionRegistryTable.nextAttempt] = null
        this[ProjectionRegistryTable.error] = null
    }
}

private fun ResultRow.toProjectionState() = ProjectionState(
    key = ProjectionKey(this[ProjectionRegistryTable.ortRunId], this[ProjectionRegistryTable.projectionType]),
    schemaVersion = this[ProjectionRegistryTable.schemaVersion],
    status = this[ProjectionRegistryTable.status],
    attempts = this[ProjectionRegistryTable.attempts],
    nextAttempt = this[ProjectionRegistryTable.nextAttempt],
    error = this[ProjectionRegistryTable.error]
)

/**
 * A [Clock] that returns the time of the database, which is the start time of the current transaction. It must be
 * called within a transaction.
 */
internal object DatabaseClock : Clock {
    override fun now(): Instant =
        checkNotNull(
            TransactionManager.current().exec("SELECT now()") { resultSet ->
                resultSet.next()
                resultSet.getTimestamp(1).toInstant().toKotlinInstant()
            }
        )
}
