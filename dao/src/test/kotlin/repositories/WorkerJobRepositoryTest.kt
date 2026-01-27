/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.model.repositories.WorkerJobRepository

/**
 * An abstract test class that contains common tests for all [WorkerJobRepository] implementations.
 */
abstract class WorkerJobRepositoryTest<T : WorkerJob> : StringSpec() {
    abstract fun createJob(): T

    abstract fun getJobRepository(): WorkerJobRepository<T>

    init {
        "start should mark a job as running" {
            val job = createJob()

            val updatedStartedAt = Clock.System.now()

            val updateResult = getJobRepository().start(job.id, updatedStartedAt)

            listOf(updateResult, getJobRepository().get(job.id)).forAll {
                it.shouldNotBeNull()
                it.startedAt shouldBe updatedStartedAt.toDatabasePrecision()
                it.status shouldBe JobStatus.RUNNING
            }
        }

        "tryStart should mark a job as running" {
            val job = createJob()

            val updatedStartedAt = Clock.System.now()

            val updateResult = getJobRepository().tryStart(job.id, updatedStartedAt)

            listOf(updateResult, getJobRepository().get(job.id)).forAll {
                it.shouldNotBeNull()
                it.startedAt shouldBe updatedStartedAt.toDatabasePrecision()
                it.status shouldBe JobStatus.RUNNING
            }
        }

        "tryStart should not change an already started job" {
            val job = createJob()

            val updatedStartedAt = Clock.System.now()
            getJobRepository().start(job.id, updatedStartedAt)

            val updateResult = getJobRepository().tryStart(job.id, updatedStartedAt.plus(10.seconds))

            updateResult should beNull()

            getJobRepository().get(job.id) shouldNotBeNull {
                startedAt shouldBe updatedStartedAt.toDatabasePrecision()
                status shouldBe JobStatus.RUNNING
            }
        }

        "tryStart should fail for a non-existing job" {
            shouldThrow<IllegalArgumentException> {
                getJobRepository().tryStart(-1, Clock.System.now())
            }
        }

        "complete should mark a job as completed" {
            val job = createJob()

            val updatedFinishedAt = Clock.System.now()
            val updateStatus = JobStatus.FINISHED

            val updateResult = getJobRepository().complete(job.id, updatedFinishedAt, updateStatus)

            listOf(updateResult, getJobRepository().get(job.id)).forAll {
                it.shouldNotBeNull()
                it.finishedAt shouldBe updatedFinishedAt.toDatabasePrecision()
                it.status shouldBe updateStatus
            }
        }

        "tryComplete should mark a job as completed" {
            val job = createJob()

            val updatedFinishedAt = Clock.System.now()
            val updateStatus = JobStatus.FINISHED

            val updateResult = getJobRepository().tryComplete(job.id, updatedFinishedAt, updateStatus)

            listOf(updateResult, getJobRepository().get(job.id)).forAll {
                it.shouldNotBeNull()
                it.finishedAt shouldBe updatedFinishedAt.toDatabasePrecision()
                it.status shouldBe updateStatus
            }
        }

        "tryComplete should not change an already completed job" {
            val job = createJob()

            val updatedFinishedAt = Clock.System.now()
            val updateStatus = JobStatus.FAILED
            getJobRepository().complete(job.id, updatedFinishedAt, updateStatus)

            val updateResult =
                getJobRepository().tryComplete(job.id, updatedFinishedAt.plus(10.seconds), JobStatus.FINISHED)

            updateResult should beNull()

            getJobRepository().get(job.id) shouldNotBeNull {
                finishedAt shouldBe updatedFinishedAt.toDatabasePrecision()
                status shouldBe updateStatus
            }
        }

        "tryComplete should fail for a non-existing job" {
            shouldThrow<IllegalArgumentException> {
                getJobRepository().tryComplete(-1, Clock.System.now(), JobStatus.FAILED)
            }
        }

        "listActive should return an active job" {
            val job = createJob()

            val activeJobs = getJobRepository().listActive()

            activeJobs shouldContainExactly listOf(job)
        }

        "listActive should not return a completed job" {
            val job = createJob()
            getJobRepository().complete(job.id, Clock.System.now(), JobStatus.FINISHED)

            val activeJobs = getJobRepository().listActive()

            activeJobs should beEmpty()
        }

        "listActive should return an active job before a given reference date" {
            val job = createJob()
            val referenceDate = job.createdAt.plus(1.seconds)

            val activeJobs = getJobRepository().listActive(referenceDate)

            activeJobs shouldContainExactly listOf(job)
        }

        "listActive should not return an active job after a given reference date" {
            val job = createJob()
            val referenceDate = job.createdAt.minus(1.seconds)

            val activeJobs = getJobRepository().listActive(referenceDate)

            activeJobs should beEmpty()
        }
    }
}
