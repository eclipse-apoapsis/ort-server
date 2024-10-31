/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.logaccess.loki

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.config.ConfigManager

class LokiConfigTest : StringSpec({
    "An instance should be created from a ConfigManager" {
        val limit = 4321
        val username = "lokiUser"
        val password = "lokiPass"
        val tenant = "testTenant"
        val timeout = "17"
        val configMap = mapOf(
            "lokiServerUrl" to SERVER_URL,
            "lokiNamespace" to NAMESPACE,
            "lokiQueryLimit" to limit,
            "lokiUsername" to username,
            "lokiPassword" to password,
            "lokiTenantId" to tenant,
            "lokiTimeoutSec" to timeout
        )
        val configManager = createConfigManager(configMap)
        val expectedConfig = LokiConfig(SERVER_URL, NAMESPACE, limit, username, password, tenant, timeout.toInt())

        val lokiConfig = LokiConfig.create(configManager)

        lokiConfig shouldBe expectedConfig
    }

    "An instance should be created with default properties" {
        val configMap = mapOf(
            "lokiServerUrl" to SERVER_URL,
            "lokiNamespace" to NAMESPACE
        )
        val configManager = createConfigManager(configMap)
        val expectedConfig = LokiConfig(SERVER_URL, NAMESPACE, 1000, null, null)

        val lokiConfig = LokiConfig.create(configManager)

        lokiConfig shouldBe expectedConfig
    }
})

/**
 * Return a [ConfigManager] object with a configuration that contains the properties of the provided [configMap].
 */
private fun createConfigManager(configMap: Map<String, Any>): ConfigManager =
    ConfigManager(
        ConfigFactory.parseMap(configMap),
        { mockk() },
        { mockk() },
        allowSecretsFromConfig = true
    )

private const val SERVER_URL = "https://loki.example.org"
private const val NAMESPACE = "test_namespace"
