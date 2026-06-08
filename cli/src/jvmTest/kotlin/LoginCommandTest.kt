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
import io.kotest.matchers.string.shouldContain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify

import org.eclipse.apoapsis.ortserver.cli.OrtServerMain
import org.eclipse.apoapsis.ortserver.cli.model.AuthenticationStorage
import org.eclipse.apoapsis.ortserver.cli.model.HostAuthenticationDetails
import org.eclipse.apoapsis.ortserver.cli.model.Tokens
import org.eclipse.apoapsis.ortserver.client.auth.AuthService
import org.eclipse.apoapsis.ortserver.client.auth.TokenInfo

class LoginCommandTest : StringSpec({
    afterEach { unmockkAll() }

    "Auth login command" should {
        "store the authentication information when using username and password" {
            mockkConstructor(AuthService::class)
            coEvery {
                anyConstructed<AuthService>().generateToken("testUser", "testPassword", setOf("offline_access"))
            } returns TokenInfo(
                accessToken = "testAccessToken",
                refreshToken = "testRefreshToken",
                expiresInSeconds = 3600
            )

            mockkObject(AuthenticationStorage)
            every { AuthenticationStorage.store(any()) } just runs

            val command = OrtServerMain()

            val result = command.test(
                listOf(
                    "auth",
                    "login",
                    "--url", "http://localhost:8080/",
                    "--token-url", "http://localhost/token",
                    "--username", "testUser",
                    "--password", "testPassword",
                    "--client-id", "test-client-id"
                )
            )

            verify(exactly = 1) {
                AuthenticationStorage.store(
                    HostAuthenticationDetails(
                        baseUrl = "http://localhost:8080/",
                        tokenUrl = "http://localhost/token",
                        clientId = "test-client-id",
                        username = "testUser",
                        tokens = Tokens("testAccessToken", "testRefreshToken")
                    )
                )
            }

            result.stdout.trimEnd() shouldBe "Successfully logged in to 'http://localhost:8080/' as 'testUser'."
        }

        "store the authentication information when using an offline token" {
            mockkConstructor(AuthService::class)
            coEvery {
                anyConstructed<AuthService>().refreshToken("testOfflineToken", setOf("offline_access"))
            } returns TokenInfo(
                accessToken = "testAccessToken",
                refreshToken = "testRefreshToken",
                expiresInSeconds = 3600
            )

            mockkObject(AuthenticationStorage)
            every { AuthenticationStorage.store(any()) } just runs

            val command = OrtServerMain()

            val result = command.test(
                listOf(
                    "auth",
                    "login",
                    "--url", "http://localhost:8080/",
                    "--token-url", "http://localhost/token",
                    "--token", "testOfflineToken",
                    "--client-id", "test-client-id"
                )
            )

            verify(exactly = 1) {
                AuthenticationStorage.store(
                    HostAuthenticationDetails(
                        baseUrl = "http://localhost:8080/",
                        tokenUrl = "http://localhost/token",
                        clientId = "test-client-id",
                        username = null,
                        tokens = Tokens("testAccessToken", "testRefreshToken")
                    )
                )
            }

            result.stdout.trimEnd() shouldBe "Successfully logged in to 'http://localhost:8080/' with an offline token."
        }

        "fail when offline token is combined with password credentials" {
            val command = OrtServerMain()

            val result = command.test(
                listOf(
                    "auth",
                    "login",
                    "--url", "http://localhost:8080/",
                    "--token", "testOfflineToken",
                    "--username", "testUser",
                    "--password", "testPassword"
                )
            )

            result.statusCode shouldBe 1
            result.stderr shouldContain "option --username and --password cannot be used with --offline-token"
        }

        "fail when only one password credential is provided" {
            val command = OrtServerMain()

            val result = command.test(
                listOf(
                    "auth",
                    "login",
                    "--url", "http://localhost:8080/",
                    "--username", "testUser"
                )
            )

            result.statusCode shouldBe 1
            result.stderr shouldContain "missing option --password"
        }

        "fail when no authentication input is provided" {
            val command = OrtServerMain()

            val result = command.test(
                listOf(
                    "auth",
                    "login",
                    "--url", "http://localhost:8080/"
                )
            )

            result.statusCode shouldBe 1
            result.stderr shouldContain "Either --username and --password or --offline-token must be provided."
        }
    }
})
