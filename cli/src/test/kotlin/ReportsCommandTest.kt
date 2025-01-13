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
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.unmockkAll

import org.eclipse.apoapsis.ortserver.cli.OrtServerMain
import org.eclipse.apoapsis.ortserver.client.OrtServerClient
import org.eclipse.apoapsis.ortserver.client.OrtServerClientConfig
import org.eclipse.apoapsis.ortserver.client.api.RunsApi

class ReportsCommandTest : StringSpec({
    afterEach { unmockkAll() }

    "download reports command" should {
        "download the requested reports" {
            val ortServerConfig = OrtServerClientConfig(
                baseUrl = "http://localhost:8080",
                tokenUrl = "http://localhost/token",
                username = "testUser",
                password = "testPassword",
                clientId = "test-client-id"
            )

            val runsMock = mockk<RunsApi> {
                coEvery { downloadReport(any(), "example1.txt", any()) } just runs
                coEvery { downloadReport(any(), "example2.txt", any()) } just runs
            }

            mockkConstructor(OrtServerClient::class)
            every { anyConstructed<OrtServerClient>().runs } returns runsMock

            val command = OrtServerMain()
            val result = command.test(
                listOf(
                    "--base-url", ortServerConfig.baseUrl,
                    "--token-url", ortServerConfig.tokenUrl,
                    "--client-id", ortServerConfig.clientId,
                    "--username", ortServerConfig.username,
                    "--password", ortServerConfig.password,
                    "runs",
                    "download",
                    "reports",
                    "--run-id", "1",
                    "--filenames", "example1.txt,example2.txt",
                    "--output-dir", "/tmp/output"
                )
            )

            coVerify(exactly = 1) {
                runsMock.downloadReport(1, "example1.txt", any())
            }
            coVerify(exactly = 1) {
                runsMock.downloadReport(1, "example2.txt", any())
            }

            result.output.trimEnd() shouldBe "/tmp/output/example1.txt\n/tmp/output/example2.txt"
        }
    }
})
