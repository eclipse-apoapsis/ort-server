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

import com.typesafe.config.Config

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.secrets.vault.model.VaultCredentials
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull

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
    val rootPath: String,

    /**
     * The path prefix under which the secrets engine is located. Vault allows enabling the KV secrets engine under
     * different paths. Using this property, the concrete path prefix to use can be specified.
     */
    val prefix: String = DEFAULT_PREFIX,

    /**
     * Defines the namespace. Namespaces are a feature of the enterprise version of vault supporting the separation
     * of multiple tenants. In an environment that uses namespaces, it is necessary to pass the target namespace as
     * a header when sending requests to the Vault service. If this property is not *null*, such a header is added.
     */
    val namespace: String? = null
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

        /** Name of the configuration property defining the prefix for paths. */
        private const val PREFIX_PROPERTY = "vaultPrefix"

        /** Name of the configuration property defining the namespace to be passed to the vault service. */
        private const val NAMESPACE_PROPERTY = "vaultNamespace"

        /** The default path prefix under which the KV Secrets Engine is available. */
        private const val DEFAULT_PREFIX = "secret"

        /** The separator for hierarchical paths. */
        private const val PATH_SEPARATOR = "/"

        /**
         * Create a new [VaultConfiguration] based on the properties stored in the given [configManager].
         */
        fun create(configManager: ConfigManager): VaultConfiguration {
            return VaultConfiguration(
                vaultUri = configManager.getString(URI_PROPERTY),
                credentials = VaultCredentials(
                    configManager.getSecret(Path(ROLE_ID_PROPERTY)),
                    configManager.getSecret(Path(SECRET_ID_PROPERTY))
                ),
                rootPath = getOptionalRootPath(configManager),
                prefix = getOptionalPrefix(configManager),
                namespace = configManager.getStringOrNull(NAMESPACE_PROPERTY)
            )
        }

        /**
         * Return the root path from the given [config] in a form, so that it can be used in a convenient way:
         * - If no root path has been provided, return an empty string.
         * - If a root path is defined, make sure that it has a trailing separator character.
         */
        private fun getOptionalRootPath(config: Config): String =
            config.getStringOrNull(ROOT_PATH_PROPERTY)?.let { rootPath ->
                rootPath.takeIf { it.endsWith(PATH_SEPARATOR) } ?: "$rootPath$PATH_SEPARATOR"
            }.orEmpty()

        /**
         * Return the prefix for paths from the given [config] or the default prefix.
         */
        private fun getOptionalPrefix(config: Config): String =
            config.getStringOrNull(PREFIX_PROPERTY)?.removeSuffix(PATH_SEPARATOR) ?: DEFAULT_PREFIX
    }
}
