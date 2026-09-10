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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

import io.ktor.server.application.install
import io.ktor.server.testing.testApplication

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify

import java.net.Authenticator

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.shared.authenticator.InfraSecretResolverFun
import org.eclipse.apoapsis.ortserver.shared.authenticator.OrtServerAuthenticator

import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

import org.ossreviewtoolkit.utils.authentication.OrtAuthenticator

class AuthenticatorTest : WordSpec({
    afterEach {
        unmockkAll()
        OrtAuthenticator.uninstall()
        stopKoin()
    }

    "configureAuthenticator()" should {
        "install the OrtServerAuthenticator as the default authenticator" {
            runTestApplication(mockk()) {
                Authenticator.getDefault().shouldBeInstanceOf<OrtServerAuthenticator>()
            }
        }

        "resolve infrastructure secrets via the ConfigManager" {
            val secretPath = Path("gitConfigFileProviderToken")
            val secretValue = "s3cr3t"
            val configManager = mockk<ConfigManager> {
                every { getSecret(secretPath) } returns secretValue
            }

            mockkObject(OrtServerAuthenticator)
            every { OrtServerAuthenticator.install(any(), any()) } returns mockk(relaxed = true)

            runTestApplication(configManager) {
                val slotResolver = slot<InfraSecretResolverFun>()
                verify {
                    OrtServerAuthenticator.install(capture(slotResolver), any())
                }

                slotResolver.captured(secretPath) shouldBe secretValue
            }
        }

        "uninstall the OrtServerAuthenticator when the application stops" {
            runTestApplication(mockk()) {
                Authenticator.getDefault().shouldBeInstanceOf<OrtServerAuthenticator>()
            }

            Authenticator.getDefault() shouldBe null
        }
    }
})

/**
 * Run a test application that uses the given [configManager] and applies the outbound authentication configuration.
 * Execute the given [block] while the application is running.
 */
private suspend fun runTestApplication(configManager: ConfigManager, block: () -> Unit) {
    testApplication {
        application {
            install(Koin) {
                modules(module { single { configManager } })
            }

            configureAuthenticator()
        }

        startApplication()

        block()
    }
}
