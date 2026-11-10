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

import java.util.UUID

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.shared.coroutines.Virtual

import org.jetbrains.exposed.v1.jdbc.Database

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ProjectionService::class.java)

/** The interval in which the state of a projection is checked while a request waits for its build. */
private val REQUEST_POLL_INTERVAL = 100.milliseconds

/**
 * The number of times the lease on a build is renewed within the lease duration. Renewing it several times allows
 * single renewals to fail, for example because the database is temporarily unavailable, before the lease expires.
 */
private const val HEARTBEATS_PER_LEASE = 3

/** The result of [requesting][ProjectionService.request] a projection. */
sealed interface ProjectionResult {
    /** The projection is ready and can be queried. */
    data object Ready : ProjectionResult

    /** The projection is being built. The caller should try again later. */
    data object Building : ProjectionResult

    /** The build of the projection failed with the given [error]. It is retried on a later request. */
    data class Failed(val error: String) : ProjectionResult
}

/**
 * A service that builds projections on demand.
 *
 * Projections are requested via [request]. If a projection is missing or outdated, a build is requested in the
 * [registry], and the build is started immediately in the background if this instance has capacity for it. Builds
 * that could not be started immediately, builds of crashed instances, and failed builds are picked up by a recovery
 * loop, which is started with [start]. Running builds are released when the service is [stopped][stop], so that
 * other instances can take them over. The service reports [metrics] for builds and requests, and the recovery loop
 * also updates the number of projections in each status.
 */
class ProjectionService(
    private val db: Database,
    private val registry: ProjectionRegistry,
    builders: List<ProjectionBuilder>,
    private val config: ProjectionConfig,
    private val owner: String = createOwnerId()
) {
    private val buildersByType = builders.associateBy { it.type }.also { buildersByType ->
        require(buildersByType.size == builders.size) {
            "The types of the projection builders must be unique, but got: ${builders.map { it.type }}"
        }
    }

    /** The schema versions of the builders by the type of their projections. */
    private val schemaVersions = buildersByType.mapValues { (_, builder) -> builder.schemaVersion }

    /** The metrics of this service, which must be bound to a meter registry to be published. */
    val metrics = ProjectionMetrics(buildersByType.keys)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Virtual)

    private val permits = Semaphore(config.maxParallelBuilds)

    /**
     * Request the projection with the given [key] and return its current state. If the projection is missing or was
     * built for an outdated schema version, a build is requested. The request then waits for the build to finish, no
     * matter which instance runs it, and returns [ProjectionResult.Building] if it is still running after that time.
     * If the caller can handle an asynchronous response, as indicated by [respondAsync], the request waits up to
     * [ProjectionConfig.asyncRequestWait], otherwise up to [ProjectionConfig.syncRequestWait].
     *
     * Schema versions only move forward: If the projection was requested for a newer schema version by an instance
     * with a newer version of the builder, for example during a rolling upgrade, it is neither used nor rebuilt, and
     * [ProjectionResult.Building] is returned right away.
     */
    suspend fun request(key: ProjectionKey, respondAsync: Boolean = true): ProjectionResult {
        val builder = getBuilder(key.type)
        val startTime = TimeSource.Monotonic.markNow()

        return getOrBuild(key, builder, respondAsync).also { result ->
            metrics.recordRequest(key.type, result, startTime.elapsedNow())
        }
    }

    private suspend fun getOrBuild(
        key: ProjectionKey,
        builder: ProjectionBuilder,
        respondAsync: Boolean
    ): ProjectionResult {
        // Record the access before reading the state and no matter the status, so that the projection cannot be deleted
        // between checking that it is ready and querying its data, or while it is being rebuilt.
        registry.touch(key, ACCESS_UPDATE_INTERVAL)
        val state = registry.get(key)

        if (state?.status == ProjectionStatus.READY && state.schemaVersion == builder.schemaVersion) {
            return ProjectionResult.Ready
        }

        val wait = if (respondAsync) config.asyncRequestWait else config.syncRequestWait
        val deadline = TimeSource.Monotonic.markNow() + wait
        var currentState = requestBuild(key, builder)

        while (true) {
            if (currentState.schemaVersion > builder.schemaVersion) return ProjectionResult.Building

            when (currentState.status) {
                ProjectionStatus.READY, ProjectionStatus.FAILED -> {
                    if (currentState.schemaVersion == builder.schemaVersion) return currentState.toResult()

                    // The projection was built for an older schema version in the meantime, so request a new build.
                    currentState = requestBuild(key, builder)
                }

                ProjectionStatus.PENDING, ProjectionStatus.BUILDING -> Unit
            }

            if (deadline.hasPassedNow()) return ProjectionResult.Building

            delay(REQUEST_POLL_INTERVAL)
            currentState = getState(key)
        }
    }

    /**
     * Request a build of the projection with the given [key] for the schema version of the given [builder], start it
     * if it is pending, and return the state of the projection.
     */
    private suspend fun requestBuild(key: ProjectionKey, builder: ProjectionBuilder): ProjectionState {
        registry.requestBuild(key, builder.schemaVersion)

        return getState(key).also { state ->
            if (state.status == ProjectionStatus.PENDING) tryStartBuild(key, builder.schemaVersion)
        }
    }

    /**
     * Start the loop that claims pending builds, builds with expired leases, and failed builds. Builds that were
     * abandoned too often are marked as failed instead.
     */
    fun start(): Job =
        scope.launch {
            while (isActive) {
                @Suppress("TooGenericExceptionCaught")
                try {
                    registry.failAbandonedBuilds(config.maxAttempts)
                    claimPendingBuilds()
                    metrics.updateStatusCounts(registry.countByTypeAndStatus())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Failed to claim pending projection builds or to update the metrics.", e)
                }

                delay(config.recoveryInterval)
            }
        }

    /** Stop all running builds and release them, so that other instances can take them over. */
    suspend fun stop() {
        scope.coroutineContext[Job]?.cancelAndJoin()

        val released = registry.release(owner)
        if (released > 0) logger.info("Released $released running projection builds.")
    }

    private suspend fun getState(key: ProjectionKey): ProjectionState =
        checkNotNull(registry.get(key)) { "The projection $key was deleted while requesting it." }

    private fun getBuilder(type: String): ProjectionBuilder =
        requireNotNull(buildersByType[type]) { "There is no builder for projections of type '$type'." }

    /**
     * Start the build of the projection with the given [key] for the given [schemaVersion] if there is capacity and it
     * can be claimed.
     */
    private suspend fun tryStartBuild(key: ProjectionKey, schemaVersion: Int) {
        tryLaunchBuild {
            key.takeIf { registry.claim(key, schemaVersion, owner, config.leaseDuration) }
        }
    }

    /** Claim and start builds as long as there is capacity and there are builds to claim. */
    private suspend fun claimPendingBuilds() {
        do {
            val launched = tryLaunchBuild {
                registry.claimNext(schemaVersions, owner, config.leaseDuration, config.maxAttempts)
            }
        } while (launched)
    }

    /**
     * Acquire a permit and launch the build of the projection that is returned by [claim], if there is capacity for
     * another build. The permit is released if nothing was claimed, also if [claim] fails or is cancelled. Nothing is
     * claimed after the service was [stopped][stop], because the build could not be run anymore and would block the
     * projection until its lease expires. For the same reason, a build that was claimed while the service was stopped
     * is released again. Return `true` if a build was launched.
     */
    private suspend fun tryLaunchBuild(claim: suspend () -> ProjectionKey?): Boolean {
        if (!scope.isActive || !permits.tryAcquire()) return false

        var key: ProjectionKey? = null

        try {
            key = claim()
        } finally {
            if (key == null) permits.release()
        }

        if (key == null) return false

        if (!scope.isActive) {
            // The service was stopped while the build was claimed, so stop() might have released the builds of this
            // instance before the claim was committed. If the service is stopped after this check, stop() releases
            // the build.
            permits.release()
            registry.release(owner)
            return false
        }

        launchBuild(key)
        return true
    }

    /** Launch the build of the projection with the given [key], which must be claimed and hold a permit. */
    private fun launchBuild(key: ProjectionKey) {
        scope.launch {
            try {
                build(key)
            } finally {
                permits.release()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun build(key: ProjectionKey) {
        val builder = getBuilder(key.type)
        val heartbeat = launchHeartbeat(key)
        val startTime = TimeSource.Monotonic.markNow()

        try {
            logger.info("Building projection $key.")

            db.transaction {
                builder.build(key.ortRunId)

                if (!registry.markReady(key, owner, builder.schemaVersion)) throw LeaseLostException(key)
            }

            val duration = startTime.elapsedNow()
            logger.info("Built projection $key in $duration.")
            metrics.recordBuild(key.type, BuildOutcome.READY, duration)
        } catch (e: LeaseLostException) {
            logger.warn(e.message)
            metrics.recordBuild(key.type, BuildOutcome.LEASE_LOST, startTime.elapsedNow())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to build projection $key.", e)
            metrics.recordBuild(key.type, BuildOutcome.FAILED, startTime.elapsedNow())

            val error = e.message ?: e::class.simpleName.orEmpty()
            registry.markFailed(key, owner, error, config.retryDelay)
        } finally {
            heartbeat.cancel()
        }
    }

    /**
     * Launch a coroutine that renews the lease on the build of the projection with the given [key]. If renewing the
     * lease fails, for example because the database is temporarily unavailable, it is retried with the next heartbeat.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun launchHeartbeat(key: ProjectionKey): Job =
        scope.launch {
            while (isActive) {
                delay(config.leaseDuration / HEARTBEATS_PER_LEASE)

                val renewed = try {
                    registry.heartbeat(key, owner, config.leaseDuration)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("Failed to renew the lease on the build of projection $key.", e)
                    continue
                }

                if (!renewed) {
                    logger.warn("Lost the lease on the build of projection $key.")
                    return@launch
                }
            }
        }

    companion object {
        /**
         * The minimum interval between two updates of the time of the last access to a projection. Projections must be
         * kept for longer than this interval after their last access, so that they are not deleted while they are
         * requested.
         */
        val ACCESS_UPDATE_INTERVAL = 1.days
    }
}

private fun ProjectionState.toResult(): ProjectionResult =
    when (status) {
        ProjectionStatus.READY -> ProjectionResult.Ready
        ProjectionStatus.FAILED -> ProjectionResult.Failed(error.orEmpty())
        ProjectionStatus.PENDING, ProjectionStatus.BUILDING -> ProjectionResult.Building
    }

/** An exception to indicate that the lease on the build of the projection with the given [key] was lost. */
private class LeaseLostException(key: ProjectionKey) :
    Exception("Discarding the build of projection $key, because the lease was lost.")

/** Create an ID that identifies this instance as the owner of leases. */
private fun createOwnerId(): String {
    val host = System.getenv("HOSTNAME") ?: "unknown"
    return "$host-${UUID.randomUUID()}"
}
