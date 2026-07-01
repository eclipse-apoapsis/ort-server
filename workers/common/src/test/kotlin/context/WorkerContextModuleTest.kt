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

package org.eclipse.apoapsis.ortserver.workers.common.context

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass

import org.eclipse.apoapsis.ortserver.components.secrets.SecretService
import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.dao.test.withMockDatabaseModule
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.secrets.SecretValue

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.mock.MockProvider
import org.koin.test.mock.declareMock

class WorkerContextModuleTest : KoinTest, StringSpec() {
    init {
        afterEach {
            stopKoin()
        }

        "A resolver service wrapping the secret service should be returned" {
            val secret = mockk<Secret>()
            val secretValue = SecretValue("The correctly resolved secret value")

            runModuleTest {
                declareMock<SecretService> {
                    every { getSecretValue(secret) } returns secretValue
                }

                val secretResolverService = get<SecretResolverService>()

                secretResolverService.getSecretValue(secret) shouldBe secretValue
            }
        }

        "A custom secret resolver service should be returned" {
            val secret = mockk<Secret>()
            val secretValue = SecretValue("The correctly resolved secret value")
            val resolverService = mockk<SecretResolverService> {
                every { getSecretValue(secret) } returns secretValue
            }

            runModuleTest(workerContextModule(resolverService)) {
                val secretResolverService = get<SecretResolverService>()

                secretResolverService.getSecretValue(secret) shouldBe secretValue
            }
        }
    }

    /**
     * Prepare the test environment for a test of the given [workerModule] and then execute the given test [block].
     */
    private suspend fun runModuleTest(workerModule: Module = workerContextModule(), block: suspend () -> Unit) {
        withMockDatabaseModule {
            startKoin {
                modules(
                    databaseModule(),
                    workerModule
                )
            }

            MockProvider.register { mockkClass(it) }

            block()
        }
    }
}
