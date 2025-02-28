/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.readText

import io.mockk.spyk

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.LogLevel
import org.eclipse.apoapsis.ortserver.api.v1.model.LogSource
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.client.NotFoundException
import org.eclipse.apoapsis.ortserver.client.api.RunsApi
import org.eclipse.apoapsis.ortserver.client.createOrtHttpClient

class RunsApiTest : StringSpec({
    "getOrtRun" should {
        "return an ORT run" {
            val respondOrtRun = OrtRun(
                id = 1,
                index = 1,
                organizationId = 1,
                productId = 1,
                repositoryId = 1,
                revision = "main",
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                jobConfigs = JobConfigurations(),
                status = OrtRunStatus.CREATED,
                jobs = Jobs(),
                issues = emptyList(),
                traceId = null,
                labels = emptyMap()
            )

            val mockEngine = MockEngine { jsonRespond(respondOrtRun, HttpStatusCode.OK) }
            val client = createOrtHttpClient(engine = mockEngine)

            val runsApi = RunsApi(client)

            runsApi.getOrtRun(1) shouldBe respondOrtRun
        }

        "throw an exception if the ORT run does not exist" {
            val mockEngine = MockEngine { respondError(HttpStatusCode.NotFound) }
            val client = createOrtHttpClient(engine = mockEngine)

            val runsApi = RunsApi(client)

            shouldThrow<NotFoundException> {
                runsApi.getOrtRun(1)
            }
        }
    }

    "downloadLogs" should {
        "execute the streamTarget lambda with the content of the logs" {
            val mockResponseContent = "mock log content"
            val mockEngine = MockEngine { respond(mockResponseContent) }
            val client = createOrtHttpClient(engine = mockEngine)

            val runsApi = RunsApi(client)

            val receivedContent = StringBuilder()
            runsApi.downloadLogs(1, null, emptyList()) { channel ->
                receivedContent.append(channel.readRemaining().readText())
            }

            receivedContent.toString() shouldBe mockResponseContent
        }

        "use the server's default log level and steps if none are specified" {
            val mockResponseContent = "mock log content"
            val mockEngine = MockEngine { respond(mockResponseContent) }
            val client = spyk(createOrtHttpClient(engine = mockEngine))

            val runsApi = RunsApi(client)

            runsApi.downloadLogs(runId = 1, streamTarget = {})

            mockEngine.requestHistory.size shouldBe 1
            with(mockEngine.requestHistory.first()) {
                url.fullPath shouldBe "/api/v1/runs/1/logs"
            }
        }

        "use the server's default log level if only steps are specified" {
            val mockResponseContent = "mock log content"
            val mockEngine = MockEngine { respond(mockResponseContent) }
            val client = spyk(createOrtHttpClient(engine = mockEngine))

            val runsApi = RunsApi(client)

            runsApi.downloadLogs(runId = 1, steps = listOf(LogSource.ANALYZER), streamTarget = {})

            mockEngine.requestHistory.size shouldBe 1
            with(mockEngine.requestHistory.first()) {
                url.fullPath shouldBe "/api/v1/runs/1/logs?steps=ANALYZER"
            }
        }

        "use the server's default steps if only log level is specified" {
            val mockResponseContent = "mock log content"
            val mockEngine = MockEngine { respond(mockResponseContent) }
            val client = spyk(createOrtHttpClient(engine = mockEngine))

            val runsApi = RunsApi(client)

            runsApi.downloadLogs(runId = 1, level = LogLevel.WARN, streamTarget = {})

            mockEngine.requestHistory.size shouldBe 1
            with(mockEngine.requestHistory.first()) {
                url.fullPath shouldBe "/api/v1/runs/1/logs?level=WARN"
            }
        }

        "include the log level and steps in the request" {
            val mockResponseContent = "mock log content"
            val mockEngine = MockEngine { respond(mockResponseContent) }
            val client = spyk(createOrtHttpClient(engine = mockEngine))

            val runsApi = RunsApi(client)

            runsApi.downloadLogs(1, LogLevel.INFO, listOf(LogSource.CONFIG, LogSource.ANALYZER)) {}

            mockEngine.requestHistory.size shouldBe 1
            with(mockEngine.requestHistory.first()) {
                url.fullPath shouldBe "/api/v1/runs/1/logs?level=INFO&steps=CONFIG%2CANALYZER"
            }
        }
    }

    "downloadReport" should {
        "Execute the streamTarget lambda with the content of the report" {
            val mockResponseContent = "mock report content"
            val mockEngine = MockEngine { respond(mockResponseContent) }
            val client = createOrtHttpClient(engine = mockEngine)

            val runsApi = RunsApi(client)

            val receivedContent = StringBuilder()
            runsApi.downloadReport(1, "report.txt") { channel ->
                receivedContent.append(channel.readRemaining().readText())
            }

            receivedContent.toString() shouldBe mockResponseContent
        }
    }
})
