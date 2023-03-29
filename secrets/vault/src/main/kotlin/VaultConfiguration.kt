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

import com.typesafe.config.Config

import org.ossreviewtoolkit.server.secrets.vault.model.VaultCredentials

/**
 * A data class storing the supported configuration options for the HashiVault secrets provider implementation.
 */
data class VaultConfiguration(
    /** The URI of the Vault service. */
    val vaultUri: String,

    /** The credentials for obtaining an authorized token. */
    val credentials: VaultCredentials,

    /**
     * Defines a root path for secrets. All paths passed to the secrets provider are interpreted as relative paths
     * from this root path. Using this mechanism, different parts of the vault storage can be made available to
     * different clients.
     */
    val rootPath: String
) {
    companion object {
        /** Name of the configuration property for the URI of the Vault service. */
        private const val URI_PROPERTY = "vaultUri"

        /** Name of the configuration property for the role ID assigned to this client application. */
        private const val ROLE_ID_PROPERTY = "vaultRoleId"

        /** Name of the configuration property for the secret ID assigned to this client application. */
        private const val SECRET_ID_PROPERTY = "vaultSecretId"

        /** Name of the configuration property defining the root path in Vault. */
        private const val ROOT_PATH_PROPERTY = "vaultRootPath"

        /** The separator for hierarchical paths. */
        private const val PATH_SEPARATOR = "/"

        /**
         * Create a new [VaultConfiguration] based on the properties stored in the given [config].
         */
        fun create(config: Config): VaultConfiguration {
            return VaultConfiguration(
                vaultUri = config.getString(URI_PROPERTY),
                credentials = VaultCredentials(
                    config.getString(ROLE_ID_PROPERTY),
                    config.getString(SECRET_ID_PROPERTY)
                ),
                rootPath = getOptionalRootPath(config)
            )
        }

        /**
         * Return the root path from the given [config] in a form, so that it can be used in a convenient way:
         * - If no root path has been provided, return an empty string.
         * - If a root path is defined, make sure that is has a trailing separator character.
         */
        private fun getOptionalRootPath(config: Config): String =
            if (config.hasPath(ROOT_PATH_PROPERTY)) {
                val rootPath = config.getString(ROOT_PATH_PROPERTY)
                rootPath.takeIf { it.endsWith(PATH_SEPARATOR) } ?: "$rootPath$PATH_SEPARATOR"
            } else {
                ""
            }
    }
}
