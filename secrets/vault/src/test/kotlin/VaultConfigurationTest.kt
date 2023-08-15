/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.secrets.vault

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.ConfigSecretProviderFactoryForTesting
import org.ossreviewtoolkit.server.secrets.vault.model.VaultCredentials

private const val VAULT_URI = "https://vault.example.org:8765"
private const val ROLE_ID = "test-role"
private const val SECRET_ID = "777"
private const val ROOT_PATH = "/path/to/my/secrets/"
private const val PREFIX = "my-secrets"
private const val NAMESPACE = "my/name/space"

class VaultConfigurationTest : StringSpec({
    "An instance can be created from a ConfigManager" {
        val properties = mapOf(
            "vaultUri" to VAULT_URI,
            "vaultRootPath" to ROOT_PATH,
            "vaultPrefix" to PREFIX,
            "vaultNamespace" to NAMESPACE
        )
        val configManager = createConfigManager(properties)

        val vaultConfig = VaultConfiguration.create(configManager)

        vaultConfig.vaultUri shouldBe VAULT_URI
        vaultConfig.rootPath shouldBe ROOT_PATH
        vaultConfig.credentials shouldBe VaultCredentials(ROLE_ID, SECRET_ID)
        vaultConfig.prefix shouldBe PREFIX
        vaultConfig.namespace shouldBe NAMESPACE
    }

    "Default values are set" {
        val properties = mapOf(
            "vaultUri" to VAULT_URI
        )
        val configManager = createConfigManager(properties)

        val vaultConfig = VaultConfiguration.create(configManager)

        vaultConfig.rootPath shouldBe ""
        vaultConfig.prefix shouldBe "secret"
        vaultConfig.namespace should beNull()
    }

    "A trailing slash is added to the root path if necessary" {
        val properties = mapOf(
            "vaultUri" to VAULT_URI,
            "vaultRootPath" to ROOT_PATH.removeSuffix("/")
        )
        val configManager = createConfigManager(properties)

        val vaultConfig = VaultConfiguration.create(configManager)

        vaultConfig.rootPath shouldBe ROOT_PATH
    }

    "A trailing slash is removed from the prefix if necessary" {
        val properties = mapOf(
            "vaultUri" to VAULT_URI,
            "vaultPrefix" to "$PREFIX/"
        )
        val configManager = createConfigManager(properties)

        val vaultConfig = VaultConfiguration.create(configManager)

        vaultConfig.prefix shouldBe PREFIX
    }
})

/**
 * Return a [ConfigManager] that wraps the given [vaultProperties]. In addition, access to the secrets is possible.
 */
private fun createConfigManager(vaultProperties: Map<String, Any>): ConfigManager {
    val secretProperties = mapOf(
        "vaultRoleId" to ROLE_ID,
        "vaultSecretId" to SECRET_ID
    )
    val configManagerProperties = mapOf(
        ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
        ConfigSecretProviderFactoryForTesting.SECRETS_PROPERTY to secretProperties
    )
    val properties = vaultProperties + mapOf(ConfigManager.CONFIG_MANAGER_SECTION to configManagerProperties)

    return ConfigManager.create(ConfigFactory.parseMap(properties))
}
