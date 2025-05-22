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

package org.eclipse.apoapsis.ortserver.secrets

import java.util.ServiceLoader

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path as ConfigPath
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId

/**
 * A class providing convenient access to secrets based on a [SecretsProvider].
 *
 * This class takes care of the instantiation of a [SecretsProvider] based on the application configuration via the
 * [createStorage] function. This provider is then wrapped, and a richer API to deal with [Secret]s is implemented on
 * top of it.
 *
 * The extended functionality compared to [SecretsProvider] is mainly related to the handling of missing [Secret]s
 * and exception handling. There are functions that require a [Secret] to exist or throw an exception otherwise.
 * With regard to exception handling, in general all exceptions thrown by the underlying [SecretsProvider] are caught
 * and wrapped in a [SecretStorageException]; so, it should be sufficient to catch this exception type. Alternatively,
 * consumers can choose to use functions that return [Result] objects.
 */
class SecretStorage(
    /** The underlying provider for secrets. */
    private val provider: SecretsProvider
) {
    companion object {
        /** A prefix used for configuration properties related to the secrets provider implementation. */
        const val CONFIG_PREFIX = "secretsProvider"

        /** The name of the configuration property defining the name of the secrets provider implementation. */
        const val NAME_PROPERTY = "name"

        /** The service loader for loading [SecretsProviderFactory] implementations available on the classpath. */
        private val LOADER = ServiceLoader.load(SecretsProviderFactory::class.java)

        /**
         * Return an initialized [SecretStorage] implementation based on the given [configManager]. This function
         * obtains the sub configuration defined by [CONFIG_PREFIX] from the given [configManager]. There it looks up
         * the name of the desired [SecretsProviderFactory] via the [NAME_PROPERTY] property. It then tries to find a
         * factory with this name via the service loader mechanism and uses this to create a [SecretsProvider]. A
         * [SecretStorage] instance wrapping this [SecretsProvider] is returned.
         */
        fun createStorage(configManager: ConfigManager): SecretStorage {
            if (!configManager.hasPath("$CONFIG_PREFIX.$NAME_PROPERTY")) {
                throw SecretStorageException(
                    """
                    Configuration property '$CONFIG_PREFIX$NAME_PROPERTY' is not set. Please set it to the name of the
                    SecretProvider implementation to use.
                """.trimIndent()
                )
            }

            val providerConfig = configManager.subConfig(ConfigPath(CONFIG_PREFIX))
            val factoryName = providerConfig.getString(NAME_PROPERTY)
            val factory = LOADER.find { it.name == factoryName }
                ?: throw SecretStorageException("SecretsProviderFactory '$factoryName' not found on classpath.")

            val provider = factory.createProvider(providerConfig)
            return SecretStorage(provider)
        }
    }

    /**
     * Return the [Secret] at the given [path] or `null` if the path cannot be resolved.
     */
    fun readSecret(path: Path): Secret? = wrapExceptions { provider.readSecret(path) }

    /**
     * Return the [Secret] at the given [path] or fail with a [SecretStorageException] if the path cannot be resolved.
     */
    fun getSecret(path: Path): Secret =
        readSecret(path) ?: throw SecretStorageException("No secret found at path '$path'.")

    /**
     * Return a [Result] with a nullable [Secret] found at the given [path]. This function works like [readSecret],
     * but wraps an occurring exception inside a [Result]. Exceptions from the underlying [SecretsProvider] are
     * wrapped in a [SecretStorageException].
     */
    fun readSecretCatching(path: Path): Result<Secret?> = runCatching { readSecret(path) }

    /**
     * Return a [Result] with the [Secret] found at the given [path]. This function works like [getSecret], but
     * wraps an occurring exception inside a [Result]. Exceptions from the underlying [SecretsProvider] are wrapped
     * in a [SecretStorageException]. If the given [path] cannot be resolved, a failed [Result] is returned as well.
     */
    fun getSecretCatching(path: Path): Result<Secret> = runCatching { getSecret(path) }

    /**
     * Store the given [secret] under the given [path] in the underlying [SecretsProvider]. Throw a
     * [SecretStorageException] if this fails.
     */
    fun writeSecret(path: Path, secret: Secret) {
        wrapExceptions { provider.writeSecret(path, secret) }
    }

    /**
     * Store the given [secret] under the given [path] in the underlying [SecretsProvider] and return a [Result] for
     * the outcome of the operation. Exceptions thrown by the [SecretsProvider] are wrapped in a
     * [SecretStorageException] and returned in the [Result].
     */
    fun writeSecretCatching(path: Path, secret: Secret): Result<Unit> = runCatching { writeSecret(path, secret) }

    /**
     * Remove the [Secret] under the given [path]. Throw a [SecretStorageException] if this fails.
     */
    fun removeSecret(path: Path) {
        wrapExceptions { provider.removeSecret(path) }
    }

    /**
     * Remove the [Secret] under the given [path] and return a [Result] for the outcome of the operation. Exceptions
     * thrown by the [SecretsProvider] are wrapped in a [SecretStorageException] and returned in the [Result].
     */
    fun removeSecretCatching(path: Path): Result<Unit> = runCatching { removeSecret(path) }

    /**
     * Generate a [Path] for the secret basing on the [organizationId], [productId] or [repositoryId] they belong to
     * and the [secretName].
     */
    fun createPath(organizationId: Long?, productId: Long?, repositoryId: Long?, secretName: String) =
        wrapExceptions {
            val ids = listOfNotNull(
                organizationId?.let { OrganizationId(it) },
                productId?.let { ProductId(it) },
                repositoryId?.let { RepositoryId(it) }
            )

            provider.createPath(ids.single(), secretName)
        }
}

/**
 * An exception class for reporting problems related to [SecretStorage] and the interaction with the underlying
 * [SecretsProvider].
 */
class SecretStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Execute the given [block] and catch all exceptions it might throw. A caught exception is then wrapped in a
 * [SecretStorageException] which is rethrown.
 */
@Suppress("TooGenericExceptionCaught")
private fun <T> wrapExceptions(block: () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        throw SecretStorageException("Exception from SecretsProvider", e)
    }
