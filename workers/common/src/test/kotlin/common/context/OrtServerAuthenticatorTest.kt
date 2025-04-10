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

package org.eclipse.apoapsis.ortserver.workers.common.context

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

import java.net.Authenticator
import java.net.URI

class OrtServerAuthenticatorTest : WordSpec() {
    /** Stores the original authenticator, so that it can be restored after the test. */
    private var originalAuthenticator: Authenticator? = null

    init {
        beforeEach {
            originalAuthenticator = Authenticator.getDefault()
        }

        afterEach {
            Authenticator.setDefault(originalAuthenticator)
        }

        "install()" should {
            "check whether the authenticator is already installed" {
                val authenticator = OrtServerAuthenticator.install()

                OrtServerAuthenticator.install() shouldBeSameInstanceAs authenticator
            }
        }

        "uninstall()" should {
            "restore the previous authenticator" {
                val orgAuthenticator = mockk<Authenticator>()
                Authenticator.setDefault(orgAuthenticator)

                OrtServerAuthenticator.install()
                OrtServerAuthenticator.uninstall() shouldBe orgAuthenticator

                Authenticator.getDefault() shouldBe orgAuthenticator
            }

            "not change anything if the ORT Server authenticator is not installed" {
                val orgAuthenticator = mockk<Authenticator>()
                Authenticator.setDefault(orgAuthenticator)

                OrtServerAuthenticator.uninstall() shouldBe orgAuthenticator

                Authenticator.getDefault() shouldBe orgAuthenticator
            }
        }

        "getPasswordAuthentication()" should {
            "return null if no matching infrastructure service is found" {
                OrtServerAuthenticator.install()

                val pwd =
                    Authenticator.requestPasswordAuthentication("example.com", null, 443, "tcp", "hello", "https")

                pwd should beNull()
            }

            "not invoke a registered authentication listener for a failed authentication" {
                val listener = mockk<AuthenticationListener>()
                OrtServerAuthenticator.install().updateAuthenticationListener(listener)

                Authenticator.requestPasswordAuthentication("example.com", null, 443, "tcp", "hello", "https")

                verify(exactly = 0) {
                    listener.onAuthentication(any())
                }
            }

            "return an authentication for a matching host name" {
                val authenticator = OrtServerAuthenticator.install()
                authenticator.updateAuthenticatedServices(
                    listOf(
                        AuthenticatedService("service", "https://example.com", USERNAME, PASSWORD)
                    )
                )

                val pwd =
                    Authenticator.requestPasswordAuthentication("example.com", null, 443, "tcp", "hello", "https")

                pwd.userName shouldBe USERNAME
                pwd.password shouldBe PASSWORD.toCharArray()
            }

            "return an authentication for a matching service URL" {
                val url = "https://repo.example.com/org/repo"

                val authenticator = OrtServerAuthenticator.install()
                authenticator.updateAuthenticatedServices(
                    listOf(
                        AuthenticatedService("service", url, USERNAME, PASSWORD)
                    )
                )

                val pwd = Authenticator.requestPasswordAuthentication(
                    "host.does.not.matter",
                    null,
                    443,
                    "tcp",
                    "hello",
                    "https",
                    URI.create(url).toURL(),
                    Authenticator.RequestorType.SERVER
                )

                pwd.userName shouldBe USERNAME
                pwd.password shouldBe PASSWORD.toCharArray()
            }

            "invoke a registered authentication listener for a successful authentication" {
                val serviceName = "matchingService"
                val url = "https://repo.example.com/org/repo"

                val listener = mockk<AuthenticationListener> {
                    every { onAuthentication(any()) } just runs
                }

                val authenticator = OrtServerAuthenticator.install()
                authenticator.updateAuthenticationListener(listener)
                authenticator.updateAuthenticatedServices(
                    listOf(
                        AuthenticatedService(serviceName, url, USERNAME, PASSWORD)
                    )
                )

                Authenticator.requestPasswordAuthentication(
                    "host.does.not.matter",
                    null,
                    443,
                    "tcp",
                    "hello",
                    "https",
                    URI.create(url).toURL(),
                    Authenticator.RequestorType.SERVER
                )

                verify(exactly = 1) {
                    listener.onAuthentication(AuthenticationEvent(serviceName))
                }
            }

            "return null for a proxy authentication" {
                val url = "https://repo.example.com/org/repo"

                val authenticator = OrtServerAuthenticator.install()
                authenticator.updateAuthenticatedServices(
                    listOf(
                        AuthenticatedService("service", url, USERNAME, PASSWORD)
                    )
                )

                val pwd = Authenticator.requestPasswordAuthentication(
                    "repo.example.com",
                    null,
                    443,
                    "tcp",
                    "hello",
                    "https",
                    URI.create(url).toURL(),
                    Authenticator.RequestorType.PROXY
                )

                pwd should beNull()
            }

            "return the credentials for the best-matching service URL" {
                val url = "https://repo.example.com/org/repo"

                val authenticator = OrtServerAuthenticator.install()
                authenticator.updateAuthenticatedServices(
                    listOf(
                        AuthenticatedService("s1", "https://repo.example.com", "user1", "password1"),
                        AuthenticatedService("s2", "https://repo2.example.org/org/repo", "user2", "password2"),
                        AuthenticatedService("s3", "https://repo.example.com/org", "user3", "password3"),
                        AuthenticatedService("s4", url, USERNAME, PASSWORD),
                        AuthenticatedService("s5", "$url/sub-repo", "user5", "password5"),
                        AuthenticatedService("s6", "${url}sitory", "user6", "password6"),
                        AuthenticatedService("s7", "https://repo.example.com/org/repo2", "user7", "password7")
                    )
                )

                val pwd = Authenticator.requestPasswordAuthentication(
                    "repo.example.com",
                    null,
                    443,
                    "tcp",
                    "hello",
                    "https",
                    URI.create(url).toURL(),
                    Authenticator.RequestorType.SERVER
                )

                pwd.userName shouldBe USERNAME
                pwd.password shouldBe PASSWORD.toCharArray()
            }
        }

        "updateAuthenticatedServices()" should {
            "ignore services with invalid URLs" {
                val url = "https://repo.example.com/org/repo"

                val authenticator = OrtServerAuthenticator.install()
                authenticator.updateAuthenticatedServices(
                    listOf(
                        AuthenticatedService("someService", "https://repo.example.com", "user1", "password1"),
                        AuthenticatedService("matchingService", url, USERNAME, PASSWORD),
                        AuthenticatedService("invalidService", "?! an invalid URL :-(", "user7", "password7")
                    )
                )

                val pwd = Authenticator.requestPasswordAuthentication(
                    "repo.example.com",
                    null,
                    443,
                    "tcp",
                    "hello",
                    "https",
                    URI.create(url).toURL(),
                    Authenticator.RequestorType.SERVER
                )

                pwd.userName shouldBe USERNAME
                pwd.password shouldBe PASSWORD.toCharArray()
            }
        }
    }
}

/** A test username. */
private const val USERNAME = "scott"

/** A test password. */
private const val PASSWORD = "tiger"
