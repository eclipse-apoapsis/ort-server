/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import com.github.ajalt.clikt.command.test

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll

import org.eclipse.apoapsis.ortserver.api.v1.model.LogLevel
import org.eclipse.apoapsis.ortserver.api.v1.model.LogSource
import org.eclipse.apoapsis.ortserver.cli.OrtServerMain
import org.eclipse.apoapsis.ortserver.cli.utils.createOrtServerClient
import org.eclipse.apoapsis.ortserver.client.OrtServerClient
import org.eclipse.apoapsis.ortserver.client.api.RunsApi

class LogsCommandTest : StringSpec({
    afterEach { unmockkAll() }

    "download logs command" should {
        "download the requested logs" {
            val runsMock = mockk<RunsApi> {
                coEvery { downloadLogs(any(), any(), any(), any()) } just runs
            }
            val ortServerClientMock = mockk<OrtServerClient> {
                every { runs } returns runsMock
            }
            mockkStatic(::createOrtServerClient)
            every { createOrtServerClient() } returns ortServerClientMock

            val command = OrtServerMain()
            val result = command.test(
                listOf(
                    "runs",
                    "download",
                    "logs",
                    "--run-id", "1",
                    "--output-dir", "/tmp/output",
                    "--level", "WARN",
                    "--steps", "${LogSource.ANALYZER.name},${LogSource.ADVISOR.name}"
                )
            )

            coVerify(exactly = 1) {
                runsMock.downloadLogs(1, LogLevel.WARN, listOf(LogSource.ANALYZER, LogSource.ADVISOR), any())
            }

            result.output.trimEnd() shouldBe "/tmp/output/run-1-WARN.logs.zip"
        }
    }
})
