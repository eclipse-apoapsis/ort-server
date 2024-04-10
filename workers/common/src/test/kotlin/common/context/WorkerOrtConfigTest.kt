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

package org.eclipse.apoapsis.ortserver.workers.common.context

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll

import org.eclipse.apoapsis.ortserver.config.ConfigManager

import org.ossreviewtoolkit.utils.common.EnvironmentVariableFilter

class WorkerOrtConfigTest : WordSpec({
    afterSpec {
        unmockkAll()
    }

    "create" should {
        "create a new instance from a given ConfigManager" {
            val configManager = mockk<ConfigManager>()

            val workerOrtConfig = WorkerOrtConfig.create(configManager)

            workerOrtConfig.configManager shouldBe configManager
        }

        "create a new instance from the application configuration" {
            val applicationConfig = ConfigFactory.empty()
            mockkStatic(ConfigFactory::class)
            every { ConfigFactory.load() } returns applicationConfig

            val workerOrtConfig = WorkerOrtConfig.create()

            workerOrtConfig.configManager.config shouldBe applicationConfig
        }
    }

    "setUpOrtEnvironment" should {
        "handle a missing ort section in the configuration" {
            val configManager = ConfigManager.create(ConfigFactory.empty())
            val workerOrtConfig = WorkerOrtConfig.create(configManager)

            workerOrtConfig.setUpOrtEnvironment()

            EnvironmentVariableFilter.isAllowed("someKey") shouldBe false
            EnvironmentVariableFilter.isAllowed("GRADLE_USER_HOME") shouldBe true
        }

        "configure the environment variable filter" {
            val ortConfig = mapOf(
                WorkerOrtConfig.ENV_ALLOW_NAMES_PROPERTY to "allowedKey,otherKey",
                WorkerOrtConfig.ENV_DENY_SUBSTRINGS_PROPERTY to "secret,forbidden"
            )
            val properties = mapOf(
                WorkerOrtConfig.ORT_CONFIG_SECTION to ortConfig
            )
            val configManager = ConfigManager.create(ConfigFactory.parseMap(properties))

            val workerOrtConfig = WorkerOrtConfig.create(configManager)
            workerOrtConfig.setUpOrtEnvironment()

            listOf("allowedKey", "otherKey").forAll { key ->
                EnvironmentVariableFilter.isAllowed(key) shouldBe true
            }

            listOf("secretTest", "forbiddenKey").forAll { key ->
                EnvironmentVariableFilter.isAllowed(key) shouldBe false
            }

            EnvironmentVariableFilter.reset()
        }

        "support default values for the environment variable filter" {
            val properties = mapOf(
                WorkerOrtConfig.ORT_CONFIG_SECTION to mapOf("foo" to "bar")
            )
            val configManager = ConfigManager.create(ConfigFactory.parseMap(properties))

            val workerOrtConfig = WorkerOrtConfig.create(configManager)
            workerOrtConfig.setUpOrtEnvironment()

            EnvironmentVariableFilter.isAllowed("someKey") shouldBe false
            EnvironmentVariableFilter.isAllowed("GRADLE_USER_HOME") shouldBe true
        }
    }
})
