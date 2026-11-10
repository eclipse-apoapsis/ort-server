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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.micrometer.core.instrument.simple.SimpleMeterRegistry

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.spyk

import java.util.Collections

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.measureTime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.transaction

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

class ProjectionServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var registry: ProjectionRegistry
    lateinit var key: ProjectionKey
    val services = mutableListOf<ProjectionService>()

    fun createService(
        builder: ProjectionBuilder,
        config: ProjectionConfig = ProjectionConfig(asyncRequestWait = Duration.ZERO),
        projectionRegistry: ProjectionRegistry = registry
    ) = ProjectionService(
        dbExtension.db,
        projectionRegistry,
        listOf(builder),
        config.copy(recoveryInterval = 50.milliseconds)
    ).also { services += it }

    suspend fun buildProjection(service: ProjectionService) {
        service.request(key)

        eventually(TIMEOUT) {
            service.request(key) shouldBe ProjectionResult.Ready
        }
    }

    suspend fun getLastAccessedAt(): Instant =
        dbExtension.db.transaction {
            ProjectionRegistryTable.select(ProjectionRegistryTable.lastAccessedAt)
                .where { ProjectionRegistryTable.ortRunId eq key.ortRunId }
                .single()[ProjectionRegistryTable.lastAccessedAt]
        }

    beforeEach {
        registry = ProjectionRegistry(dbExtension.db)
        key = ProjectionKey(dbExtension.fixtures.ortRun.id, TYPE)
    }

    afterEach {
        services.forEach { service -> service.stop() }
        services.clear()
    }

    "request" should {
        "build a missing projection" {
            val builder = TestBuilder()
            val service = createService(builder)

            service.request(key) shouldBe ProjectionResult.Building

            eventually(TIMEOUT) {
                service.request(key) shouldBe ProjectionResult.Ready
            }

            builder.builtRuns should containExactly(key.ortRunId)
        }

        "build a projection only once for concurrent requests" {
            val builder = TestBuilder()
            val service = createService(builder)

            coroutineScope {
                (1..10).map { async { service.request(key) } }.awaitAll()
            }

            eventually(TIMEOUT) {
                service.request(key) shouldBe ProjectionResult.Ready
            }

            builder.builtRuns should containExactly(key.ortRunId)
        }

        "rebuild a projection if the schema version changed" {
            val builder = TestBuilder()
            buildProjection(createService(builder))

            val newBuilder = TestBuilder(schemaVersion = 2)
            val newService = createService(newBuilder)

            newService.request(key) shouldBe ProjectionResult.Building

            eventually(TIMEOUT) {
                newService.request(key) shouldBe ProjectionResult.Ready
            }

            newBuilder.builtRuns should containExactly(key.ortRunId)
            registry.get(key)?.schemaVersion shouldBe 2
        }

        "return the error of a failed build" {
            val service = createService(TestBuilder { error("Something went wrong.") })

            service.request(key)

            eventually(TIMEOUT) {
                service.request(key) shouldBe ProjectionResult.Failed("Something went wrong.")
            }
        }

        "roll back the data of a failed build" {
            val service = createService(
                TestBuilder { ortRunId ->
                    dbExtension.fixtures.ortRunRepository.delete(ortRunId)
                    error("Something went wrong.")
                }
            )

            service.request(key)

            eventually(TIMEOUT) {
                registry.get(key)?.status shouldBe ProjectionStatus.FAILED
            }

            dbExtension.fixtures.ortRunRepository.get(key.ortRunId)?.id shouldBe key.ortRunId
        }

        "wait for a build that finishes within the request wait time" {
            val builder = TestBuilder()
            val service = createService(builder, ProjectionConfig(asyncRequestWait = TIMEOUT))

            service.request(key) shouldBe ProjectionResult.Ready

            builder.builtRuns should containExactly(key.ortRunId)
        }

        "wait for a build that fails within the request wait time" {
            val service = createService(
                TestBuilder { error("Something went wrong.") },
                ProjectionConfig(asyncRequestWait = TIMEOUT)
            )

            service.request(key) shouldBe ProjectionResult.Failed("Something went wrong.")
        }

        "stop waiting for a build after the request wait time" {
            val buildCanFinish = CompletableDeferred<Unit>()
            val service = createService(
                TestBuilder { buildCanFinish.await() },
                ProjectionConfig(asyncRequestWait = 200.milliseconds)
            )

            service.request(key) shouldBe ProjectionResult.Building

            buildCanFinish.complete(Unit)

            eventually(TIMEOUT) {
                service.request(key) shouldBe ProjectionResult.Ready
            }
        }

        "wait for the sync request wait time if the caller does not accept an asynchronous response" {
            val builder = TestBuilder()
            val service = createService(
                builder,
                ProjectionConfig(asyncRequestWait = Duration.ZERO, syncRequestWait = TIMEOUT)
            )

            service.request(key, respondAsync = false) shouldBe ProjectionResult.Ready

            builder.builtRuns should containExactly(key.ortRunId)
        }

        "neither use nor rebuild a projection that was built for a newer schema version" {
            buildProjection(createService(TestBuilder(schemaVersion = 2)))

            val oldBuilder = TestBuilder(schemaVersion = 1)
            val oldService = createService(oldBuilder, ProjectionConfig(asyncRequestWait = TIMEOUT))

            lateinit var result: ProjectionResult
            measureTime { result = oldService.request(key) } shouldBeLessThan TIMEOUT / 2

            result shouldBe ProjectionResult.Building
            oldBuilder.builtRuns should beEmpty()

            registry.get(key) shouldNotBeNull {
                status shouldBe ProjectionStatus.READY
                schemaVersion shouldBe 2
            }
        }

        "replace a running build for an older schema version" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, "old-owner", 1.minutes)

            val builder = TestBuilder(schemaVersion = 2)
            val service = createService(builder, ProjectionConfig(asyncRequestWait = TIMEOUT))

            service.request(key) shouldBe ProjectionResult.Ready

            builder.builtRuns should containExactly(key.ortRunId)
            registry.get(key)?.schemaVersion shouldBe 2
            registry.markReady(key, "old-owner", 1) shouldBe false
        }

        "record an access to a projection that is rebuilt" {
            buildProjection(createService(TestBuilder()))
            dbExtension.db.transaction {
                ProjectionRegistryTable.update { it[lastAccessedAt] = Clock.System.now() - 10.days }
            }

            createService(TestBuilder(schemaVersion = 2)).request(key)

            getLastAccessedAt() shouldBeGreaterThan Clock.System.now() - 1.days
        }

        "release the permit if claiming a build fails" {
            val registry = spyk(registry)
            coEvery {
                registry.claim(any(), any(), any(), any())
            } throws IllegalStateException("Claiming failed.") coAndThen { callOriginal() }

            val service = createService(
                TestBuilder(),
                ProjectionConfig(maxParallelBuilds = 1, asyncRequestWait = Duration.ZERO),
                registry
            )

            shouldThrow<IllegalStateException> { service.request(key) }

            eventually(TIMEOUT) {
                service.request(key) shouldBe ProjectionResult.Ready
            }
        }

        "throw an exception for an unknown projection type" {
            val service = createService(TestBuilder())

            shouldThrow<IllegalArgumentException> {
                service.request(ProjectionKey(key.ortRunId, "unknown"))
            }
        }
    }

    "start" should {
        "build projections that could not be started immediately" {
            val firstBuildCanFinish = CompletableDeferred<Unit>()
            val otherKey = ProjectionKey(dbExtension.fixtures.createOrtRun().id, TYPE)
            val builder = TestBuilder { ortRunId ->
                if (ortRunId == key.ortRunId) firstBuildCanFinish.await()
            }
            val service = createService(
                builder,
                ProjectionConfig(maxParallelBuilds = 1, asyncRequestWait = Duration.ZERO)
            )

            service.request(key)
            service.request(otherKey) shouldBe ProjectionResult.Building
            registry.get(otherKey)?.status shouldBe ProjectionStatus.PENDING

            service.start()
            firstBuildCanFinish.complete(Unit)

            eventually(TIMEOUT) {
                service.request(otherKey) shouldBe ProjectionResult.Ready
            }

            builder.builtRuns should containExactly(key.ortRunId, otherKey.ortRunId)
        }

        "take over builds with an expired lease" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, "crashed-owner", 100.milliseconds)

            val builder = TestBuilder()
            val service = createService(builder)
            service.start()

            eventually(TIMEOUT) {
                service.request(key) shouldBe ProjectionResult.Ready
            }

            builder.builtRuns should containExactly(key.ortRunId)
        }

        "not take over builds that were abandoned too often, but mark them as failed" {
            registry.requestBuild(key, 1)
            registry.claim(key, 1, "crashed-owner", 100.milliseconds)

            val builder = TestBuilder()
            val service = createService(
                builder,
                ProjectionConfig(maxAttempts = 1, asyncRequestWait = Duration.ZERO)
            )
            service.start()

            eventually(TIMEOUT) {
                registry.get(key)?.status shouldBe ProjectionStatus.FAILED
            }

            builder.builtRuns should beEmpty()

            eventually(TIMEOUT) {
                service.request(key) shouldBe ProjectionResult.Ready
            }
        }

        "release the permit if claiming a pending build fails" {
            val registry = spyk(registry)
            coEvery {
                registry.claimNext(any(), any(), any(), any())
            } throws IllegalStateException("Claiming failed.") coAndThen { callOriginal() }

            val service = createService(TestBuilder(), ProjectionConfig(maxParallelBuilds = 1), registry)
            registry.requestBuild(key, 1)

            service.start()

            eventually(TIMEOUT) {
                registry.get(key)?.status shouldBe ProjectionStatus.READY
            }
        }

        "retry failed builds" {
            var failures = 1
            val builder = TestBuilder {
                if (failures-- > 0) error("Something went wrong.")
            }
            val service = createService(
                builder,
                ProjectionConfig(retryDelay = 100.milliseconds, asyncRequestWait = Duration.ZERO)
            )

            service.request(key)
            service.start()

            eventually(TIMEOUT) {
                service.request(key) shouldBe ProjectionResult.Ready
            }
        }
    }

    "build" should {
        "keep renewing the lease if renewing it fails" {
            val registry = spyk(registry)
            coEvery {
                registry.heartbeat(any(), any(), any())
            } throws IllegalStateException("Renewing failed.") coAndThen { callOriginal() }

            val buildCanFinish = CompletableDeferred<Unit>()
            val service = createService(
                TestBuilder { buildCanFinish.await() },
                ProjectionConfig(leaseDuration = 300.milliseconds, asyncRequestWait = Duration.ZERO),
                registry
            )

            service.request(key)

            eventually(TIMEOUT) {
                coVerify(atLeast = 2) { registry.heartbeat(key, any(), any()) }
            }

            buildCanFinish.complete(Unit)

            eventually(TIMEOUT) {
                service.request(key) shouldBe ProjectionResult.Ready
            }
        }
    }

    "metrics" should {
        "record successful builds and requests" {
            val meterRegistry = SimpleMeterRegistry()
            val service = createService(TestBuilder(), ProjectionConfig(asyncRequestWait = TIMEOUT))
            service.metrics.bindTo(meterRegistry)

            service.request(key) shouldBe ProjectionResult.Ready

            eventually(TIMEOUT) {
                meterRegistry.timerCount("projections.builds", "outcome", "ready") shouldBe 1
            }

            meterRegistry.timerCount("projections.requests", "result", "ready") shouldBe 1
        }

        "record failed builds and requests" {
            val meterRegistry = SimpleMeterRegistry()
            val service = createService(
                TestBuilder { error("Something went wrong.") },
                ProjectionConfig(asyncRequestWait = TIMEOUT)
            )
            service.metrics.bindTo(meterRegistry)

            service.request(key) shouldBe ProjectionResult.Failed("Something went wrong.")

            eventually(TIMEOUT) {
                meterRegistry.timerCount("projections.builds", "outcome", "failed") shouldBe 1
            }

            meterRegistry.timerCount("projections.requests", "result", "failed") shouldBe 1
        }

        "record requests for projections that are still being built" {
            val meterRegistry = SimpleMeterRegistry()
            val buildCanFinish = CompletableDeferred<Unit>()
            val service = createService(TestBuilder { buildCanFinish.await() })
            service.metrics.bindTo(meterRegistry)

            service.request(key) shouldBe ProjectionResult.Building
            buildCanFinish.complete(Unit)

            meterRegistry.timerCount("projections.requests", "result", "building") shouldBe 1
        }

        "update the number of projections in each status in the recovery loop" {
            val meterRegistry = SimpleMeterRegistry()
            val service = createService(TestBuilder())
            service.metrics.bindTo(meterRegistry)
            buildProjection(service)

            service.start()

            eventually(TIMEOUT) {
                meterRegistry.statusCount(ProjectionStatus.READY) shouldBe 1.0
            }

            meterRegistry.statusCount(ProjectionStatus.PENDING) shouldBe 0.0
        }
    }

    "stop" should {
        "release running builds" {
            val buildStarted = CompletableDeferred<Unit>()
            val service = createService(
                TestBuilder {
                    buildStarted.complete(Unit)
                    CompletableDeferred<Unit>().await()
                }
            )

            service.request(key)
            buildStarted.await()

            service.stop()

            registry.get(key) shouldNotBeNull {
                status shouldBe ProjectionStatus.PENDING
                attempts shouldBe 0
            }
        }

        "not claim builds that are requested after the service was stopped" {
            val builder = TestBuilder()
            val service = createService(builder)

            service.stop()

            service.request(key) shouldBe ProjectionResult.Building

            registry.get(key) shouldNotBeNull {
                status shouldBe ProjectionStatus.PENDING
                attempts shouldBe 0
            }

            builder.builtRuns should beEmpty()
        }

        "release a build that was claimed after the service was stopped" {
            lateinit var service: ProjectionService
            val spiedRegistry = spyk(registry)
            coEvery { spiedRegistry.claim(any(), any(), any(), any()) } coAnswers {
                // Stop the service after it checked that it is running, but before the claim is committed.
                service.stop()
                registry.claim(key, 1, thirdArg(), 1.minutes)
            }

            val builder = TestBuilder()
            service = createService(builder, projectionRegistry = spiedRegistry)

            service.request(key) shouldBe ProjectionResult.Building

            registry.get(key) shouldNotBeNull {
                status shouldBe ProjectionStatus.PENDING
                attempts shouldBe 0
            }

            builder.builtRuns should beEmpty()
        }
    }
})

/** Return the number of recordings of the timer with the given [name] for the [TYPE] and the given tag. */
private fun SimpleMeterRegistry.timerCount(name: String, tagKey: String, tagValue: String): Long =
    get(name).tags("type", TYPE, tagKey, tagValue).timer().count()

/** Return the value of the gauge for the number of projections of the [TYPE] with the given [status]. */
private fun SimpleMeterRegistry.statusCount(status: ProjectionStatus): Double =
    get("projections.status").tags("type", TYPE, "status", status.name.lowercase()).gauge().value()

private const val TYPE = "test"
private val TIMEOUT = 10.seconds

/** A [ProjectionBuilder] for tests that records the runs it built and executes the given [block]. */
private class TestBuilder(
    override val schemaVersion: Int = 1,
    private val block: suspend (Long) -> Unit = {}
) : ProjectionBuilder {
    override val type = TYPE

    val builtRuns: MutableList<Long> = Collections.synchronizedList(mutableListOf())

    override suspend fun build(ortRunId: Long) {
        block(ortRunId)
        builtRuns += ortRunId
    }
}
