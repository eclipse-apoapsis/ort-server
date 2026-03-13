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

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.utils.config.getIntOrDefault

private const val MASTER_PASSWORD_PROPERTY = "databaseMasterPassword"
private const val SALT_PROPERTY = "databaseSalt"
private const val KEY_VERSION_PROPERTY = "databaseKeyVersion"
private const val DEFAULT_KEY_VERSION = 1
private const val MINIMUM_MASTER_PASSWORD_LENGTH = 16
private const val MINIMUM_SALT_BYTES = 16

/** Configuration for the database secrets provider. */
internal data class DatabaseSecretsConfiguration(
    val masterPassword: String,
    val salt: String,
    val keyVersion: Int
) {
    companion object {
        fun create(configManager: ConfigManager): DatabaseSecretsConfiguration {
            val masterPassword = configManager.getSecret(Path(MASTER_PASSWORD_PROPERTY))
            validateMasterPassword(masterPassword)

            val salt = configManager.getString(SALT_PROPERTY)
            validateSalt(salt)

            val keyVersion = configManager.getIntOrDefault(KEY_VERSION_PROPERTY, DEFAULT_KEY_VERSION)
            validate(keyVersion > 0) {
                "The database secrets key version must be a positive integer."
            }

            return DatabaseSecretsConfiguration(
                masterPassword = masterPassword,
                salt = salt,
                keyVersion = keyVersion
            )
        }

        private fun validateMasterPassword(masterPassword: String) {
            validate(masterPassword.isNotEmpty()) {
                "The database secrets master password must not be blank."
            }

            validate(masterPassword == masterPassword.trim()) {
                "The database secrets master password must not have leading or trailing whitespace."
            }

            validate(masterPassword.length >= MINIMUM_MASTER_PASSWORD_LENGTH) {
                "The database secrets master password must be at least $MINIMUM_MASTER_PASSWORD_LENGTH characters long."
            }
        }

        private fun validateSalt(salt: String) {
            validate(salt.isNotEmpty()) {
                "The database secrets salt must not be blank."
            }

            validate(salt == salt.trim()) {
                "The database secrets salt must not have leading or trailing whitespace."
            }

            validate(salt.matches(Regex("^[0-9a-fA-F]+$"))) {
                "The database secrets salt must be a hex-encoded string."
            }

            validate(salt.length % 2 == 0) {
                "The database secrets salt must contain an even number of hex characters."
            }

            validate(salt.length >= MINIMUM_SALT_BYTES * 2) {
                "The database secrets salt must be at least $MINIMUM_SALT_BYTES bytes (${MINIMUM_SALT_BYTES * 2} hex " +
                        "characters) long."
            }
        }
    }
}

private inline fun validate(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) throw ConfigException(lazyMessage())
}
