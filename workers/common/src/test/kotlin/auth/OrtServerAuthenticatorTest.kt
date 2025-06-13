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

package org.eclipse.apoapsis.ortserver.workers.common.auth

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URI

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.Secret

import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.ort.OrtAuthenticator

class OrtServerAuthenticatorTest : WordSpec() {
    /** Stores the original authenticator, so that it can be restored after the test. */
    private var originalAuthenticator: Authenticator? = null

    init {
        beforeEach {
            originalAuthenticator = Authenticator.getDefault()
        }

        afterEach {
            Authenticator.setDefault(originalAuthenticator)
            unmockkAll()
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
                OrtAuthenticator.uninstall() shouldBe orgAuthenticator

                Authenticator.getDefault() shouldBe orgAuthenticator
            }

            "not change anything if the ORT Server authenticator is not installed" {
                val orgAuthenticator = mockk<Authenticator>()
                Authenticator.setDefault(orgAuthenticator)

                OrtAuthenticator.uninstall() shouldBe orgAuthenticator

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
                val services = listOf(
                    createService("service", "https://example.com", usernameSecret, passwordSecret)
                )
                authenticator.updateAuthenticationInfo(createAuthInfo(services))

                val pwd =
                    Authenticator.requestPasswordAuthentication("example.com", null, 443, "tcp", "hello", "https")

                pwd.userName shouldBe USERNAME
                pwd.password shouldBe PASSWORD.toCharArray()
            }

            "return an authentication for a matching service URL" {
                val url = "https://repo.example.com/org/repo"

                val authenticator = OrtServerAuthenticator.install()
                val services = listOf(
                    createService("service", url, usernameSecret, passwordSecret)
                )
                authenticator.updateAuthenticationInfo(createAuthInfo(services))

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
                val url = "https://repo.example.com/org/repo"

                val listener = mockk<AuthenticationListener> {
                    every { onAuthentication(any()) } just runs
                }

                val authenticator = OrtServerAuthenticator.install()
                authenticator.updateAuthenticationListener(listener)
                val service = createService("matchingService", url, usernameSecret, passwordSecret)
                authenticator.updateAuthenticationInfo(createAuthInfo(listOf<InfrastructureService>(service)))

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

                val slotEvent = slot<AuthenticationEvent>()
                verify(exactly = 1) {
                    listener.onAuthentication(capture(slotEvent))
                }

                slotEvent.captured.service.name shouldBe service.name
                slotEvent.captured.service.credentialsTypes shouldBe service.credentialsTypes
            }

            "return null for a proxy authentication" {
                val url = "https://repo.example.com/org/repo"

                val authenticator = OrtServerAuthenticator.install()
                val services = listOf(
                    createService("service", url, usernameSecret, passwordSecret)
                )
                authenticator.updateAuthenticationInfo(createAuthInfo(services))

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
                val services = listOf(
                    createService("s1", "https://repo.example.com"),
                    createService("s2", "https://repo2.example.org/org/repo"),
                    createService("s3", "https://repo.example.com/org"),
                    createService("s4", url, usernameSecret, passwordSecret),
                    createService("s5", "$url/sub-repo"),
                    createService("s6", "${url}sitory"),
                    createService("s7", "https://repo.example.com/org/repo2")
                )
                authenticator.updateAuthenticationInfo(createAuthInfo(services))

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

            "return credentials from a single service for the host" {
                val url = "https://repo.example.com/org/repo"

                val authenticator = OrtServerAuthenticator.install()
                val services = listOf(
                    createService("s3", "https://repo.example.com/org/other_repo", usernameSecret, passwordSecret)
                )
                authenticator.updateAuthenticationInfo(createAuthInfo(services))

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

            "return the credential from the best-matching service if the prefix does not match" {
                val url = "https://repos.example.com/artifactory/repo1/@scope/lib/-/name/lib-1.0.0.tgz"

                val authenticator = OrtServerAuthenticator.install()
                val services = listOf(
                    createService("s1", "https://repos.example.com/artifactory2"),
                    createService("s2", "https://repos2.example.com/artifactory/repo1"),
                    createService(
                        "s3",
                        "https://repos.example.com/artifactory/api/npm/repo1",
                        usernameSecret,
                        passwordSecret
                    ),
                    createService("s4", "https://repos.example.com/artifactory/api/npm/repo2")
                )
                authenticator.updateAuthenticationInfo(createAuthInfo(services))

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

            "not return credentials if no unique best-matching service is found" {
                val url = "https://repos.example.com/artifactory/repo1/@scope/lib/-/name/lib-1.0.0.tgz"

                val authenticator = OrtServerAuthenticator.install()
                val services = listOf(
                    createService(
                        "s1",
                        "https://repos.example.com/artifactory/api/npm/repo2",
                        usernameSecret,
                        passwordSecret
                    ),
                    createService(
                        "s2",
                        "https://repos.example.com/artifactory/api/npm/repo3",
                        usernameSecret,
                        passwordSecret
                    )
                )
                authenticator.updateAuthenticationInfo(createAuthInfo(services))

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

                pwd should beNull()
            }

            "use credentials specified in the URL" {
                val url = "https://repo.example.com/org/repo"

                val authenticator = OrtServerAuthenticator.install()
                val services = listOf(
                    createService("service", url)
                )
                authenticator.updateAuthenticationInfo(createAuthInfo(services))

                val pwd = Authenticator.requestPasswordAuthentication(
                    "host.does.not.matter",
                    null,
                    443,
                    "tcp",
                    "hello",
                    "https",
                    URI.create("https://$USERNAME:$PASSWORD@repo.example.com/org/repo").toURL(),
                    Authenticator.RequestorType.SERVER
                )

                pwd.userName shouldBe USERNAME
                pwd.password shouldBe PASSWORD.toCharArray()
            }

            "not use credentials from the .netrc file" {
                val userHomeDir = tempdir()
                mockkObject(Os)
                every { Os.userHomeDirectory } returns userHomeDir

                val uri = URI.create("https://repo.example.com/org/repo")

                val netRcFile = userHomeDir.resolve(".netrc")
                netRcFile.writeText("machine repo.example.com login other_$USERNAME password other_$PASSWORD")

                OrtServerAuthenticator.install()

                val pwd = Authenticator.requestPasswordAuthentication(
                    "repo.example.com",
                    null,
                    443,
                    "tcp",
                    "hello",
                    "https",
                    uri.toURL(),
                    Authenticator.RequestorType.SERVER
                )

                pwd should beNull()
            }

            "use credentials from the original authenticator before testing infrastructure services" {
                val authUrl = URI.create("https://repo.example.com/org/repo").toURL()
                val originalAuthenticator = object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication? =
                        if (requestingURL.toString() == authUrl.toString()) {
                            PasswordAuthentication(USERNAME, PASSWORD.toCharArray())
                        } else {
                            null
                        }
                }
                Authenticator.setDefault(originalAuthenticator)

                val authenticator = OrtServerAuthenticator.install()
                val services = listOf(
                    createService("service", authUrl.toString())
                )
                authenticator.updateAuthenticationInfo(createAuthInfo(services))

                val pwd = Authenticator.requestPasswordAuthentication(
                    authUrl.host,
                    null,
                    443,
                    "tcp",
                    "hello",
                    "https",
                    authUrl,
                    Authenticator.RequestorType.SERVER
                )

                pwd.userName shouldBe USERNAME
                pwd.password shouldBe PASSWORD.toCharArray()
            }

            "ignore a service with credentials type NO_AUTHENTICATION" {
                val url = "https://repo.example.com/org/repo"

                val authenticator = OrtServerAuthenticator.install()
                val services = listOf(
                    createService(
                        "service",
                        url,
                        usernameSecret,
                        passwordSecret,
                        credentialsTypes = setOf(CredentialsType.NO_AUTHENTICATION)
                    )
                )
                authenticator.updateAuthenticationInfo(createAuthInfo(services))

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

                pwd should beNull()
            }
        }

        "updateAuthenticatedServices()" should {
            "ignore services with invalid URLs" {
                val url = "https://repo.example.com/org/repo"

                val authenticator = OrtServerAuthenticator.install()
                val services = listOf(
                    createService("someService", "https://repo.example.com"),
                    createService("matchingService", url, usernameSecret, passwordSecret),
                    createService("invalidService", "?! an invalid URL :-(")
                )
                authenticator.updateAuthenticationInfo(createAuthInfo(services))

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

        "authenticationInfo" should {
            "initially be empty" {
                val authenticator = OrtServerAuthenticator.install()

                authenticator.authenticationInfo.services should beEmpty()
                authenticator.authenticationInfo.secrets.keys should beEmpty()
            }

            "return the current authentication information" {
                val services = listOf(
                    createService("service", "https://repo.example.com")
                )
                val info = createAuthInfo(services)

                val authenticator = OrtServerAuthenticator.install()
                authenticator.updateAuthenticationInfo(info)

                authenticator.authenticationInfo shouldBe info
            }
        }
    }
}

/** A test username. */
private const val USERNAME = "scott"

/** A test password. */
private const val PASSWORD = "tiger"

/** A secret referencing the test username. */
private val usernameSecret = createSecret("username")

/** A secret referencing the test password. */
private val passwordSecret = createSecret("password")

/**
 * Create a [Secret] based on the given [path].
 */
private fun createSecret(path: String): Secret =
    Secret(0, path, "$path-secret", null, null, null, null)

/**
 * Create an [InfrastructureService] object with the given [name], [url], and optional [username], [password], and
 * [credentialsTypes].
 */
private fun createService(
    name: String,
    url: String,
    username: Secret? = null,
    password: Secret? = null,
    credentialsTypes: Set<CredentialsType> = emptySet()
): InfrastructureService =
    InfrastructureService(
        name = name,
        url = url,
        usernameSecret = username ?: createSecret("unknown-username"),
        passwordSecret = password ?: createSecret("unknown-password"),
        organization = null,
        product = null,
        credentialsTypes = credentialsTypes
    )

/**
 * Create an [AuthenticationInfo] object with the given [services] and the secrets used for testing.
 */
private fun createAuthInfo(services: List<InfrastructureService>): AuthenticationInfo =
    AuthenticationInfo(
        secrets = mapOf(
            usernameSecret.path to USERNAME,
            passwordSecret.path to PASSWORD
        ),
        services = services
    )
