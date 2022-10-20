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

package org.ossreviewtoolkit.server.workers.analyzer

import io.kotest.core.spec.style.WordSpec

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

import java.io.File

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.api.v1.AnalyzerJob
import org.ossreviewtoolkit.server.api.v1.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.api.v1.AnalyzerJobStatus

private const val JOB_ID = 1L
private val projectDir = File("src/test/resources/mavenProject/").absoluteFile

class AnalyzerWorkerTest : WordSpec({
    "AnalyzerWorker" should {
        "create and Analyzer Result" {
            val client = mockk<ServerClient>()

            val analyzerJob = AnalyzerJob(
                JOB_ID,
                Clock.System.now(),
                configuration = AnalyzerJobConfiguration(),
                status = AnalyzerJobStatus.SCHEDULED,
                startedAt = Clock.System.now(),
                repositoryUrl = "https://example.com/myProject",
                repositoryRevision = "revision"
            )

            val worker = spyk(AnalyzerWorker(client))

            coEvery { client.getScheduledAnalyzerJob() } returns analyzerJob
            coEvery { client.reportAnalyzerJobFailure(any()) }
            coEvery {
                client.finishAnalyzerJob(JOB_ID)
            } coAnswers {
                // Since the worker is in an unending loop, it should be shutdown after processing this analyzer job.
                worker.stop()
                analyzerJob.copy(status = AnalyzerJobStatus.FINISHED, finishedAt = Clock.System.now())
            }
            // To speed the test up, a minimal pom file is scanned and the repository is not cloned.
            with(worker) {
                every { any<AnalyzerJob>().download() } returns projectDir
            }

            worker.start()

            coVerify(exactly = 0) {
                client.reportAnalyzerJobFailure(any())
            }
            coVerify {
                client.getScheduledAnalyzerJob()
                client.finishAnalyzerJob(JOB_ID)
            }
        }
    }
})
