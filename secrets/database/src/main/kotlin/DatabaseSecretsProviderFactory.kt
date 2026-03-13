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

package org.eclipse.apoapsis.ortserver.secrets.database

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.secrets.SecretsProvider
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactory

import org.slf4j.LoggerFactory

/**
 * An implementation of [SecretsProviderFactory] that creates [SecretsProvider] instances storing encrypted secrets in
 * the ORT Server database.
 */
class DatabaseSecretsProviderFactory : SecretsProviderFactory {
    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseSecretsProviderFactory::class.java)
    }

    override val name = "database"

    override fun createProvider(configManager: ConfigManager): SecretsProvider {
        val config = DatabaseSecretsConfiguration.create(configManager)

        logger.info("Creating DatabaseSecretsProvider.")

        val encryptor = SpringCryptoSecretEncryptor(
            password = config.masterPassword,
            salt = config.salt,
            keyVersion = config.keyVersion
        )

        return DatabaseSecretsProvider(encryptor)
    }
}
