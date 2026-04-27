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

package org.eclipse.apoapsis.ortserver.logaccess.elasticsearch

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.config.ConfigManager

class ElasticsearchConfigTest : StringSpec({
    "An instance should be created from a ConfigManager" {
        val pageSize = 4321
        val username = "elasticUser"
        val password = "elasticPass"
        val timeout = "17"
        val configMap = mapOf(
            "elasticsearchServerUrl" to SERVER_URL,
            "elasticsearchIndex" to INDEX,
            "elasticsearchNamespace" to NAMESPACE,
            "elasticsearchPageSize" to pageSize,
            "elasticsearchUsername" to username,
            "elasticsearchPassword" to password,
            "elasticsearchTimeoutSec" to timeout
        )
        val configManager = createConfigManager(configMap)
        val expectedConfig = ElasticsearchConfig(
            serverUrl = SERVER_URL,
            index = INDEX,
            namespace = NAMESPACE,
            pageSize = pageSize,
            username = username,
            password = password,
            apiKey = null,
            timeoutSec = timeout.toInt()
        )

        ElasticsearchConfig.create(configManager) shouldBe expectedConfig
    }

    "An instance should be created with default properties" {
        val configMap = mapOf(
            "elasticsearchServerUrl" to SERVER_URL,
            "elasticsearchIndex" to INDEX,
            "elasticsearchNamespace" to NAMESPACE
        )
        val configManager = createConfigManager(configMap)
        val expectedConfig = ElasticsearchConfig(SERVER_URL, INDEX, NAMESPACE, 1000, null, null, null)

        ElasticsearchConfig.create(configManager) shouldBe expectedConfig
    }

    "An API key should take precedence over basic auth" {
        val username = "elasticUser"
        val configMap = mapOf(
            "elasticsearchServerUrl" to SERVER_URL,
            "elasticsearchIndex" to INDEX,
            "elasticsearchNamespace" to NAMESPACE,
            "elasticsearchUsername" to username,
            "elasticsearchPassword" to "ignoredPassword",
            "elasticsearchApiKey" to "api-key"
        )

        ElasticsearchConfig.create(createConfigManager(configMap)) shouldBe
            ElasticsearchConfig(SERVER_URL, INDEX, NAMESPACE, 1000, username, null, "api-key")
    }

    "Blank auth settings should be treated as absent" {
        val configMap = mapOf(
            "elasticsearchServerUrl" to SERVER_URL,
            "elasticsearchIndex" to INDEX,
            "elasticsearchNamespace" to NAMESPACE,
            "elasticsearchUsername" to "",
            "elasticsearchApiKey" to ""
        )

        ElasticsearchConfig.create(createConfigManager(configMap)) shouldBe
            ElasticsearchConfig(SERVER_URL, INDEX, NAMESPACE, 1000, null, null, null)
    }
})

private fun createConfigManager(configMap: Map<String, Any>): ConfigManager =
    ConfigManager(
        ConfigFactory.parseMap(configMap),
        { mockk() },
        { mockk() },
        allowSecretsFromConfig = true
    )

private const val SERVER_URL = "https://elasticsearch.example.org"
private const val INDEX = "ort-server-logs-*"
private const val NAMESPACE = "test_namespace"
