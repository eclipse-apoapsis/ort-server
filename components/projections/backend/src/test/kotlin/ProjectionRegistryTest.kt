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

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.selectAll

class ProjectionRegistryTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var clock: TestClock
    lateinit var registry: ProjectionRegistry
    lateinit var key: ProjectionKey

    /**
     * Start a transaction that holds a key share lock on the registry row of the projection with the given [key], like
     * a running build that inserted data into a projection table. The lock is held until the returned deferred is
     * completed.
     */
    suspend fun CoroutineScope.lockForKeyShare(key: ProjectionKey): CompletableDeferred<Unit> {
        val locked = CompletableDeferred<Unit>()
        val unlock = CompletableDeferred<Unit>()

        launch {
            dbExtension.db.transaction {
                // Only the lock is needed, but the query must be executed to acquire it.
                ProjectionRegistryTable.selectAll()
                    .where {
                        (ProjectionRegistryTable.ortRunId eq key.ortRunId) and
                                (ProjectionRegistryTable.projectionType eq key.type)
                    }
                    .forUpdate(ForUpdateOption.PostgreSQL.ForKeyShare)
                    .single()

                locked.complete(Unit)
                unlock.await()
            }
        }

        locked.await()
        return unlock
    }

    beforeEach {
        clock = TestClock(Clock.System.now().toDatabasePrecision())
        registry = ProjectionRegistry(dbExtension.db, clock)
        key = ProjectionKey(dbExtension.fixtures.ortRun.id, TYPE)
    }

    "requestBuild" should {
        "create a pending projection" {
            registry.requestBuild(key, 1) shouldBe true

            registry.get(key) shouldBe ProjectionState(
                key = key,
                schemaVersion = 1,
                status = ProjectionStatus.PENDING,
                attempts = 0,
                nextAttempt = null,
                error = null
            )
        }

        "not request a build if the projection is already pending" {
            registry.requestBuild(key, 1)

            registry.requestBuild(key, 1) shouldBe false
        }

        "request a build only once for concurrent requests" {
            val results = coroutineScope {
                (1..10).map { async { registry.requestBuild(key, 1) } }.awaitAll()
            }

            results.count { it } shouldBe 1
        }

        "not request a build if the projection is ready" {
            registry.buildSuccessfully(key, 1)

            registry.requestBuild(key, 1) shouldBe false
            registry.get(key)?.status shouldBe ProjectionStatus.READY
        }

        "request a build if the projection was built for a different schema version" {
            registry.buildSuccessfully(key, 1)

            registry.requestBuild(key, 2) shouldBe true

            registry.get(key) shouldNotBeNull {
                status shouldBe ProjectionStatus.PENDING
                schemaVersion shouldBe 2
            }
        }

        "not request a build for an older schema version" {
            registry.buildSuccessfully(key, 2)

            registry.requestBuild(key, 1) shouldBe false

            registry.get(key) shouldNotBeNull {
                status shouldBe ProjectionStatus.READY
                schemaVersion shouldBe 2
            }
        }

        "not retry a failed build for an older schema version" {
            registry.requestBuild(key, 2)
            registry.claim(key, 2, OWNER, LEASE)
            registry.markFailed(key, OWNER, "error", 1.minutes)
            clock.advance(1.minutes)

            registry.requestBuild(key, 1) shouldBe false

            registry.get(key) shouldNotBeNull {
                status shouldBe ProjectionStatus.FAILED
                schemaVersion shouldBe 2
            }
        }

        "not request a build if a failed build cannot be retried yet" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, OWNER, LEASE)
            registry.markFailed(key, OWNER, "error", 1.minutes)

            registry.requestBuild(key, 1) shouldBe false
        }

        "request a build if a failed build can be retried" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, OWNER, LEASE)
            registry.markFailed(key, OWNER, "error", 1.minutes)
            clock.advance(1.minutes)

            registry.requestBuild(key, 1) shouldBe true

            registry.get(key) shouldBe ProjectionState(
                key = key,
                schemaVersion = 1,
                status = ProjectionStatus.PENDING,
                attempts = 0,
                nextAttempt = null,
                error = null
            )
        }

        "not wait for running builds that hold key share locks on the projection" {
            registry.buildSuccessfully(key, 1)

            coroutineScope {
                val unlock = lockForKeyShare(key)
                val requested = async { registry.requestBuild(key, 2) }

                try {
                    eventually(LOCK_TIMEOUT) { requested.isCompleted shouldBe true }
                } finally {
                    unlock.complete(Unit)
                }

                requested.await() shouldBe true
            }
        }
    }

    "claim" should {
        "claim a pending build" {
            registry.requestBuild(key, 1)

            registry.claim(key, 1, OWNER, LEASE) shouldBe true

            registry.get(key) shouldNotBeNull {
                status shouldBe ProjectionStatus.BUILDING
                attempts shouldBe 1
            }
        }

        "not claim a build with an active lease" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, OWNER, LEASE)

            registry.claim(key, 1, OTHER_OWNER, LEASE) shouldBe false
        }

        "claim a build with an expired lease" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, OWNER, LEASE)
            clock.advance(LEASE + 1.minutes)

            registry.claim(key, 1, OTHER_OWNER, LEASE) shouldBe true
            registry.get(key)?.attempts shouldBe 2
        }

        "not claim a ready projection" {
            registry.buildSuccessfully(key, 1)

            registry.claim(key, 1, OWNER, LEASE) shouldBe false
        }

        "not claim a build that was requested for a different schema version" {
            registry.requestBuild(key, 2)

            registry.claim(key, 1, OWNER, LEASE) shouldBe false
        }
    }

    "claimNext" should {
        "claim the oldest pending build of the given types" {
            val key2 = ProjectionKey(dbExtension.fixtures.createOrtRun().id, TYPE)
            val otherTypeKey = ProjectionKey(key.ortRunId, "other")

            registry.requestBuild(otherTypeKey, 1)
            clock.advance(1.minutes)
            registry.requestBuild(key, 1)
            clock.advance(1.minutes)
            registry.requestBuild(key2, 1)

            registry.claimNext(mapOf(TYPE to 1), OWNER, LEASE, MAX_ATTEMPTS) shouldBe key
            registry.claimNext(mapOf(TYPE to 1), OWNER, LEASE, MAX_ATTEMPTS) shouldBe key2
            registry.claimNext(mapOf(TYPE to 1), OWNER, LEASE, MAX_ATTEMPTS) should beNull()
        }

        "never claim the same build for concurrent callers" {
            val keys = (1..5).map { ProjectionKey(dbExtension.fixtures.createOrtRun().id, TYPE) }
            keys.forEach { registry.requestBuild(it, 1) }

            val claimed = coroutineScope {
                (1..10).map {
                    async { registry.claimNext(mapOf(TYPE to 1), "owner-$it", LEASE, MAX_ATTEMPTS) }
                }.awaitAll()
            }

            claimed.filterNotNull() should containExactlyInAnyOrder(keys)
        }

        "only claim builds that were requested for the given schema versions" {
            val otherKey = ProjectionKey(dbExtension.fixtures.createOrtRun().id, TYPE)
            registry.requestBuild(key, 1)
            clock.advance(1.minutes)
            registry.requestBuild(otherKey, 2)

            registry.claimNext(mapOf(TYPE to 2), OWNER, LEASE, MAX_ATTEMPTS) shouldBe otherKey
            registry.claimNext(mapOf(TYPE to 2), OWNER, LEASE, MAX_ATTEMPTS) should beNull()
        }

        "claim a build with an expired lease" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, OWNER, LEASE)
            clock.advance(LEASE + 1.minutes)

            registry.claimNext(mapOf(TYPE to 1), OTHER_OWNER, LEASE, MAX_ATTEMPTS) shouldBe key
        }

        "claim a build with an expired lease whose build still holds key share locks on the projection" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, OWNER, LEASE)
            clock.advance(LEASE + 1.minutes)

            coroutineScope {
                val unlock = lockForKeyShare(key)

                try {
                    registry.claimNext(mapOf(TYPE to 1), OTHER_OWNER, LEASE, MAX_ATTEMPTS) shouldBe key
                } finally {
                    unlock.complete(Unit)
                }
            }
        }

        "claim a failed build once the retry delay has passed" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, OWNER, LEASE)
            registry.markFailed(key, OWNER, "error", 1.minutes)

            registry.claimNext(mapOf(TYPE to 1), OWNER, LEASE, MAX_ATTEMPTS) should beNull()

            clock.advance(1.minutes)

            registry.claimNext(mapOf(TYPE to 1), OWNER, LEASE, MAX_ATTEMPTS) shouldBe key
        }

        "not claim a failed build that reached the maximum number of attempts" {
            registry.requestBuild(key, 1)

            repeat(MAX_ATTEMPTS) {
                registry.claimNext(mapOf(TYPE to 1), OWNER, LEASE, MAX_ATTEMPTS) shouldBe key
                registry.markFailed(key, OWNER, "error", 1.minutes)
                clock.advance(1.minutes)
            }

            registry.get(key)?.attempts shouldBe MAX_ATTEMPTS
            registry.claimNext(mapOf(TYPE to 1), OWNER, LEASE, MAX_ATTEMPTS) should beNull()
        }
        "not claim a build with an expired lease that reached the maximum number of attempts" {
            registry.requestBuild(key, 1)

            repeat(MAX_ATTEMPTS) {
                registry.claimNext(mapOf(TYPE to 1), OWNER, LEASE, MAX_ATTEMPTS) shouldBe key
                clock.advance(LEASE + 1.minutes)
            }

            registry.claimNext(mapOf(TYPE to 1), OWNER, LEASE, MAX_ATTEMPTS) should beNull()
        }
    }

    "failAbandonedBuilds" should {
        "mark builds with expired leases that reached the maximum number of attempts as failed" {
            registry.requestBuild(key, 1)

            repeat(MAX_ATTEMPTS) {
                registry.claimNext(mapOf(TYPE to 1), OWNER, LEASE, MAX_ATTEMPTS) shouldBe key
                clock.advance(LEASE + 1.minutes)
            }

            registry.failAbandonedBuilds(MAX_ATTEMPTS) shouldBe 1

            registry.get(key)?.status shouldBe ProjectionStatus.FAILED
            registry.requestBuild(key, 1) shouldBe true
        }

        "keep builds that have attempts left or whose lease has not expired" {
            val otherKey = ProjectionKey(dbExtension.fixtures.createOrtRun().id, TYPE)
            registry.requestBuild(key, 1)
            registry.requestBuild(otherKey, 1)

            registry.claim(otherKey, 1, OWNER, LEASE)

            repeat(MAX_ATTEMPTS) {
                clock.advance(LEASE + 1.minutes)
                registry.claim(key, 1, OWNER, LEASE) shouldBe true
            }

            registry.failAbandonedBuilds(MAX_ATTEMPTS) shouldBe 0

            registry.get(key)?.status shouldBe ProjectionStatus.BUILDING
            registry.get(otherKey)?.status shouldBe ProjectionStatus.BUILDING
        }
    }

    "heartbeat" should {
        "extend the lease of the owner" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, OWNER, LEASE)
            clock.advance(LEASE - 1.minutes)

            registry.heartbeat(key, OWNER, LEASE) shouldBe true

            clock.advance(2.minutes)
            registry.claim(key, 1, OTHER_OWNER, LEASE) shouldBe false
        }

        "fail if the lease was lost" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, OWNER, LEASE)
            clock.advance(LEASE + 1.minutes)
            registry.claim(key, 1, OTHER_OWNER, LEASE)

            registry.heartbeat(key, OWNER, LEASE) shouldBe false
        }
    }

    "markReady" should {
        "mark the projection as ready for the given schema version" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, OWNER, LEASE)

            registry.markReady(key, OWNER, 2) shouldBe true

            registry.get(key) shouldBe ProjectionState(
                key = key,
                schemaVersion = 2,
                status = ProjectionStatus.READY,
                attempts = 0,
                nextAttempt = null,
                error = null
            )
        }

        "fail if the lease was lost" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, OWNER, LEASE)
            clock.advance(LEASE + 1.minutes)
            registry.claim(key, 1, OTHER_OWNER, LEASE)

            registry.markReady(key, OWNER, 1) shouldBe false
            registry.get(key)?.status shouldBe ProjectionStatus.BUILDING
        }
    }

    "markFailed" should {
        "store the error and the time of the next attempt" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, OWNER, LEASE)

            registry.markFailed(key, OWNER, "Something went wrong.", 5.minutes) shouldBe true

            registry.get(key) shouldBe ProjectionState(
                key = key,
                schemaVersion = 1,
                status = ProjectionStatus.FAILED,
                attempts = 1,
                nextAttempt = clock.now() + 5.minutes,
                error = "Something went wrong."
            )
        }

        "fail if the lease was lost" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, OWNER, LEASE)
            clock.advance(LEASE + 1.minutes)
            registry.claim(key, 1, OTHER_OWNER, LEASE)

            registry.markFailed(key, OWNER, "error", 1.minutes) shouldBe false
        }
    }

    "release" should {
        "make the builds of the owner pending again without counting the attempt" {
            val otherKey = ProjectionKey(dbExtension.fixtures.createOrtRun().id, TYPE)
            registry.requestBuild(key, 1)
            registry.requestBuild(otherKey, 1)
            registry.claim(key, 1, OWNER, LEASE)
            registry.claim(otherKey, 1, OTHER_OWNER, LEASE)

            registry.release(OWNER) shouldBe 1

            registry.get(key) shouldNotBeNull {
                status shouldBe ProjectionStatus.PENDING
                attempts shouldBe 0
            }

            registry.get(otherKey)?.status shouldBe ProjectionStatus.BUILDING
        }
    }

    "deleteNotAccessedSince" should {
        "delete projections that were not accessed recently" {
            registry.buildSuccessfully(key, 1)
            clock.advance(10.days)

            registry.deleteNotAccessedSince(clock.now() - 5.days) shouldBe 1

            registry.get(key) should beNull()
        }

        "keep projections that were accessed recently" {
            registry.buildSuccessfully(key, 1)
            clock.advance(10.days)
            registry.touch(key, 1.days)

            registry.deleteNotAccessedSince(clock.now() - 5.days) shouldBe 0
        }

        "keep projections that are being built" {
            registry.requestBuild(key, 1)
            clock.advance(10.days)
            registry.claim(key, 1, OWNER, LEASE)

            registry.deleteNotAccessedSince(clock.now() - 5.days) shouldBe 0
        }

        "delete builds with an expired lease that were not accessed recently" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, OWNER, LEASE)
            clock.advance(10.days)

            registry.deleteNotAccessedSince(clock.now() - 5.days) shouldBe 1

            registry.get(key) should beNull()
        }

        "be triggered by the deletion of the ORT run" {
            registry.buildSuccessfully(key, 1)

            dbExtension.fixtures.ortRunRepository.delete(key.ortRunId)

            registry.get(key) should beNull()
        }
    }

    "DatabaseClock" should {
        "return the start time of the current transaction" {
            dbExtension.db.transaction {
                val now = DatabaseClock.now()

                DatabaseClock.now() shouldBe now
                (Clock.System.now() - now).absoluteValue shouldBeLessThan 1.minutes
            }
        }
    }
})

private const val TYPE = "test"
private const val OWNER = "owner"
private const val OTHER_OWNER = "other-owner"
private const val MAX_ATTEMPTS = 3
private val LEASE = 5.minutes
private val LOCK_TIMEOUT = 5.seconds

private suspend fun ProjectionRegistry.buildSuccessfully(key: ProjectionKey, schemaVersion: Int) {
    requestBuild(key, schemaVersion)
    claim(key, schemaVersion, OWNER, LEASE)
    markReady(key, OWNER, schemaVersion)
}

/** A [Clock] for tests that only advances when told to. */
internal class TestClock(private var now: Instant) : Clock {
    override fun now(): Instant = now

    fun advance(duration: kotlin.time.Duration) {
        now += duration
    }
}
