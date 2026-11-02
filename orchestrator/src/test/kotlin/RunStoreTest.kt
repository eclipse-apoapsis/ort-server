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

package org.eclipse.apoapsis.ortserver.orchestrator

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import kotlin.time.Clock
import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.util.asPresent
import org.eclipse.apoapsis.ortserver.orchestrator.WorkerScheduleInfo.ADVISOR
import org.eclipse.apoapsis.ortserver.orchestrator.WorkerScheduleInfo.ANALYZER
import org.eclipse.apoapsis.ortserver.orchestrator.WorkerScheduleInfo.REPORTER
import org.eclipse.apoapsis.ortserver.orchestrator.WorkerScheduleInfo.SCANNER
import org.eclipse.apoapsis.ortserver.utils.test.Integration

class RunStoreTest : WordSpec({
    tags(Integration)

    val dbExtension = extension(DatabaseTestExtension())

    lateinit var fixtures: Fixtures
    lateinit var store: RunStore

    beforeEach {
        fixtures = dbExtension.fixtures
        store = RunStore(
            WorkerJobRepositories(
                fixtures.analyzerJobRepository,
                fixtures.advisorJobRepository,
                fixtures.scannerJobRepository,
                fixtures.evaluatorJobRepository,
                fixtures.reporterJobRepository,
                fixtures.notifierJobRepository
            ),
            fixtures.repositoryRepository,
            fixtures.ortRunRepository
        )
    }

    "requireRepository()" should {
        "succeed if the repository exists" {
            store.requireRepository(fixtures.repository.id)
        }

        "fail if the repository does not exist" {
            shouldThrow<IllegalArgumentException> {
                store.requireRepository(fixtures.repository.id + 1)
            }
        }
    }

    "getOrtRun()" should {
        "return the run" {
            val run = fixtures.createOrtRun()

            store.getOrtRun(run.id).id shouldBe run.id
        }

        "fail if the run does not exist" {
            shouldThrow<IllegalArgumentException> {
                store.getOrtRun(fixtures.ortRun.id + 1)
            }
        }
    }

    "loadRunState()" should {
        "take the status of the existing jobs into account" {
            val run = fixtures.createOrtRun()
            val analyzerJob = fixtures.createAnalyzerJob(run.id)
            fixtures.analyzerJobRepository.complete(analyzerJob.id, Clock.System.now(), JobStatus.FINISHED)

            store.loadRunState(run).nextJobs() should containExactlyInAnyOrder(ADVISOR, SCANNER)
        }

        "prefer the resolved job configurations of the run" {
            val run = fixtures.createOrtRun()
            fixtures.ortRunRepository.update(
                id = run.id,
                resolvedJobConfigs = JobConfigurations(
                    analyzer = fixtures.jobConfigurations.analyzer,
                    reporter = fixtures.jobConfigurations.reporter
                ).asPresent()
            )
            val analyzerJob = fixtures.createAnalyzerJob(run.id)
            fixtures.analyzerJobRepository.complete(analyzerJob.id, Clock.System.now(), JobStatus.FINISHED)

            store.loadRunState(store.getOrtRun(run.id)).nextJobs() should containExactlyInAnyOrder(REPORTER)
        }

        "pass on the failure of the Config worker" {
            val run = fixtures.createOrtRun()

            store.loadRunState(run, configWorkerFailed = true).finalStatus() shouldBe OrtRunStatus.FAILED
        }
    }

    "createJob()" should {
        "create a job with the resolved configuration of the run" {
            val resolvedConfig = AdvisorJobConfiguration(advisors = listOf("VulnerableCode"))
            val run = fixtures.ortRunRepository.update(
                id = fixtures.createOrtRun().id,
                resolvedJobConfigs = fixtures.jobConfigurations.copy(advisor = resolvedConfig).asPresent()
            )

            val job = store.createJob(run, ADVISOR).shouldNotBeNull()

            fixtures.advisorJobRepository.get(job.id).shouldNotBeNull {
                ortRunId shouldBe run.id
                configuration shouldBe resolvedConfig
                status shouldBe JobStatus.CREATED
            }
        }

        "return null if the worker is not configured" {
            val run = fixtures.createOrtRun(jobConfigurations = fixtures.jobConfigurations.copy(scanner = null))

            store.createJob(run, SCANNER) should beNull()

            fixtures.scannerJobRepository.getForOrtRun(run.id) should beNull()
        }
    }

    "markJobScheduled()" should {
        "set the status of the job to SCHEDULED" {
            val job = fixtures.createScannerJob(fixtures.ortRun.id)

            store.markJobScheduled(SCANNER, job.id)

            fixtures.scannerJobRepository.get(job.id).shouldNotBeNull().status shouldBe JobStatus.SCHEDULED
        }
    }

    "completeJob()" should {
        "complete the job with the given status and error message" {
            val job = fixtures.createAnalyzerJob(fixtures.ortRun.id)

            store.completeJob(ANALYZER, job.id, JobStatus.FAILED, "error message").ortRunId shouldBe fixtures.ortRun.id

            fixtures.analyzerJobRepository.get(job.id).shouldNotBeNull {
                status shouldBe JobStatus.FAILED
                errorMessage shouldBe "error message"
                finishedAt shouldNot beNull()
            }
        }

        "fail if the job does not exist" {
            shouldThrow<IllegalArgumentException> {
                store.completeJob(ANALYZER, 42L, JobStatus.FINISHED)
            }
        }
    }

    "tryFailJob()" should {
        "mark a running job as failed and return true" {
            val job = fixtures.createAnalyzerJob(fixtures.ortRun.id)

            store.tryFailJob(ANALYZER, fixtures.ortRun.id) shouldBe true

            fixtures.analyzerJobRepository.get(job.id).shouldNotBeNull().status shouldBe JobStatus.FAILED
        }

        "not change a job that has already been completed and return false" {
            val job = fixtures.createAnalyzerJob(fixtures.ortRun.id)
            fixtures.analyzerJobRepository.complete(job.id, Clock.System.now(), JobStatus.FINISHED)

            store.tryFailJob(ANALYZER, fixtures.ortRun.id) shouldBe false

            fixtures.analyzerJobRepository.get(job.id).shouldNotBeNull().status shouldBe JobStatus.FINISHED
        }
    }

    "addIssues() and failRun()" should {
        "add the issues to the run and set the status to FAILED" {
            val run = fixtures.createOrtRun()
            val issue1 = Issue(Instant.fromEpochSeconds(1), "scanner", "scanner issue", Severity.WARNING)
            val issue2 = Issue(Instant.fromEpochSeconds(2), "config", "config error", Severity.ERROR)

            store.addIssues(run.id, listOf(issue1))
            store.failRun(run.id, issue2)

            fixtures.ortRunRepository.get(run.id).shouldNotBeNull {
                status shouldBe OrtRunStatus.FAILED
                issues.map { it.message } should containExactly("scanner issue", "config error")
            }
        }
    }

    "finishRun()" should {
        "set the final status of the run and delete the mail recipients of the notifier job" {
            val run = fixtures.createOrtRun()
            val notifierJob = fixtures.createNotifierJob(run.id)

            store.finishRun(run.id, OrtRunStatus.FINISHED_WITH_ISSUES)

            fixtures.ortRunRepository.get(run.id).shouldNotBeNull().status shouldBe OrtRunStatus.FINISHED_WITH_ISSUES
            fixtures.notifierJobRepository.get(notifierJob.id).shouldNotBeNull()
                .configuration.recipientAddresses should beEmpty()
        }

        "set the final status of a run without a notifier job" {
            val run = fixtures.createOrtRun()

            store.finishRun(run.id, OrtRunStatus.FINISHED)

            fixtures.ortRunRepository.get(run.id).shouldNotBeNull().status shouldBe OrtRunStatus.FINISHED
        }
    }
})
