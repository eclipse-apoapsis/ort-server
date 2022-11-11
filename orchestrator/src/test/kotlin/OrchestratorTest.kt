/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.orchestrator

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.kotlinx.datetime.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.model.AnalyzerJobStatus
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.OrtRunStatus
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzeRequest
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.CreateOrtRun
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessageSender

class OrchestratorTest : WordSpec() {
    private val msgHeader = MessageHeader(
        token = "token",
        traceId = "traceId"
    )

    private val repository = Repository(
        id = 42,
        type = RepositoryType.GIT,
        url = "https://example.com/git/repository.git"
    )

    private val analyzerJob = AnalyzerJob(
        id = 123,
        ortRunId = 999,
        createdAt = Clock.System.now(),
        startedAt = null,
        finishedAt = null,
        configuration = AnalyzerJobConfiguration(),
        status = AnalyzerJobStatus.CREATED,
        repositoryUrl = repository.url,
        repositoryRevision = "main"
    )

    init {
        "handleCreateOrtRun" should {
            "create an ORT run in the database and notify the analyzer" {
                val analyzerJobRepository = mockk<AnalyzerJobRepository>()
                val repositoryRepository = mockk<RepositoryRepository>()
                val ortRunRepository = mockk<OrtRunRepository>()
                val analyzerSender = mockk<MessageSender<AnalyzeRequest>>()

                every { repositoryRepository.get(any()) } returns repository
                every { analyzerJobRepository.create(any(), any()) } returns analyzerJob
                every { analyzerJobRepository.update(any(), any(), any(), any()) } returns mockk()
                every { analyzerSender.send(any()) } just runs

                val createOrtRun = CreateOrtRun(
                    OrtRun(
                        id = 1,
                        index = 12,
                        repositoryId = repository.id,
                        revision = "main",
                        createdAt = Instant.fromEpochSeconds(0),
                        jobs = JobConfigurations(analyzerJob.configuration),
                        status = OrtRunStatus.CREATED
                    )
                )

                Orchestrator(analyzerJobRepository, repositoryRepository, ortRunRepository, analyzerSender)
                    .handleCreateOrtRun(msgHeader, createOrtRun)

                verify(exactly = 1) {
                    // The job was created in the database
                    analyzerJobRepository.create(
                        ortRunId = withArg { it shouldBe createOrtRun.ortRun.id },
                        configuration = withArg { it shouldBe analyzerJob.configuration }
                    )

                    // The message was sent.
                    analyzerSender.send(
                        message = withArg {
                            it.header shouldBe msgHeader
                            it.payload shouldBe AnalyzeRequest(repository, createOrtRun.ortRun, analyzerJob)
                        }
                    )

                    // The database entry was set to SCHEDULED.
                    analyzerJobRepository.update(
                        id = withArg { it shouldBe analyzerJob.id },
                        startedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(AnalyzerJobStatus.SCHEDULED) }
                    )
                }
            }
        }

        "handleAnalyzerWorkerResult" should {
            "update the job in the database" {
                val analyzerJobRepository = mockk<AnalyzerJobRepository>()
                val repositoryRepository = mockk<RepositoryRepository>()
                val ortRunRepository = mockk<OrtRunRepository>()
                val analyzerSender = mockk<MessageSender<AnalyzeRequest>>()

                val analyzerWorkerResult = AnalyzerWorkerResult(123)

                every { analyzerJobRepository.get(analyzerWorkerResult.jobId) } returns analyzerJob
                every { analyzerJobRepository.update(analyzerJob.id, any(), any(), any()) } returns mockk()

                Orchestrator(analyzerJobRepository, repositoryRepository, ortRunRepository, analyzerSender)
                    .handleAnalyzerWorkerResult(analyzerWorkerResult)

                verify(exactly = 1) {
                    analyzerJobRepository.update(
                        id = withArg { it shouldBe analyzerJob.id },
                        finishedAt = withArg { it.verifyTimeRange(10.seconds) },
                        status = withArg { it.verifyOptionalValue(AnalyzerJobStatus.FINISHED) }
                    )
                }
            }
        }
    }
}

private fun OptionalValue<Instant?>.verifyTimeRange(allowedDiff: Duration) {
    shouldBeTypeOf<OptionalValue.Present<Instant>>()
    val now = Clock.System.now()

    value.shouldBeBetween(now - allowedDiff, now)
}

private fun <T> OptionalValue<T>.verifyOptionalValue(expectedValue: T) {
    shouldBeTypeOf<OptionalValue.Present<T>>()

    value shouldBe expectedValue
}
