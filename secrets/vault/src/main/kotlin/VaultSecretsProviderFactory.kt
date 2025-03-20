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

package org.eclipse.apoapsis.ortserver.secrets.vault

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.secrets.SecretsProvider
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactory

import org.slf4j.LoggerFactory

/**
 * An implementation of [SecretsProviderFactory] that creates [SecretsProvider] instances allowing access to secrets
 * managed by a HashiCorp Vault service.
 */
class VaultSecretsProviderFactory : SecretsProviderFactory {
    companion object {
        private val logger = LoggerFactory.getLogger(VaultSecretsProviderFactory::class.java)
    }

    override val name: String = "vault"

    override fun createProvider(configManager: ConfigManager): SecretsProvider {
        val vaultConfig = VaultConfiguration.create(configManager)

        logger.info("Creating VaultSecretsProvider.")
        logger.info("Vault URI: '${vaultConfig.vaultUri}'.")
        logger.info("RoleId: '${vaultConfig.credentials.roleId}'.")
        logger.info("Root path: '${vaultConfig.rootPath}'.")
        logger.info("Prefix: '${vaultConfig.prefix}'.")
        logger.info("Namespace: '${vaultConfig.namespace ?: "<undefined>"}'.")

        return VaultSecretsProvider(vaultConfig)
    }
}
