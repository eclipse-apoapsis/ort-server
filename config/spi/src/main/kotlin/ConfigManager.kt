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

package org.eclipse.apoapsis.ortserver.config

import com.typesafe.config.Config

import java.io.File
import java.io.InputStream
import java.util.ServiceLoader

import org.eclipse.apoapsis.ortserver.utils.config.getBooleanOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getConfigOrEmpty
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull

/**
 * A class wrapping different configuration service provider implementations simplifying the interaction with them.
 *
 * An instance of this class can be created via the [create] factory function. It loads the configured provider
 * implementations from the classpath and initializes them. It then provides a richer interface on top of these
 * provider interfaces. It also handles occurring exceptions and wraps them into [ConfigException] exceptions.
 */
class ConfigManager(
    /** The application configuration. */
    val config: Config,

    /** The function to create the provider for configuration files. */
    private val configFileProviderSupplier: (ConfigSecretProvider) -> ConfigFileProvider,

    /** The function to create the provider for secrets from the configuration. */
    private val configSecretProviderSupplier: () -> ConfigSecretProvider,

    /**
     *  A flag whether [getSecret] should check first whether the requested secret is contained in the configuration
     *  before querying the [ConfigSecretProvider].
     */
    private val allowSecretsFromConfig: Boolean
) : Config by config {
    companion object {
        /**
         * The name of the section in the configuration that contains the settings evaluated by this class.
         */
        const val CONFIG_MANAGER_SECTION = "configManager"

        /**
         * The name of the configuration property that defines the name of the config file provider implementation.
         */
        const val FILE_PROVIDER_NAME_PROPERTY = "fileProvider"

        /**
         * The name of the configuration property that defines the name of the config secret provider implementation.
         */
        const val SECRET_PROVIDER_NAME_PROPERTY = "secretProvider"

        /**
         * The name of the configuration property that controls whether secrets can be loaded from the application
         * configuration. If this property is set to *true* (which is also the default value), the [getSecret]
         * function first checks whether a configuration property for the requested path exists. If this is the case,
         * it returns its value. Otherwise, it delegates the request to the [ConfigSecretProvider]. This behavior is
         * useful in deployments without a dedicated secret storage. In such scenarios, it is common to pass the
         * values of secrets via the same mechanisms as normal configuration properties, for instance as environment
         * variables. It is then not necessary to have a secret provider configured.
         */
        const val SECRET_FROM_CONFIG_PROPERTY = "allowSecretsFromConfig"

        /**
         * Constant for an empty configuration context. This indicates that the user has not specified a specific
         * context. The concrete meaning is up to a [ConfigFileProvider] implementation; it should fall back to some
         * meaningful default.
         */
        val EMPTY_CONTEXT = Context("")

        /** The service loader for file provider factories. */
        private val FILE_PROVIDER_LOADER = ServiceLoader.load(ConfigFileProviderFactory::class.java)

        /** The service loader for secret provider factories. */
        private val SECRET_PROVIDER_LOADER = ServiceLoader.load(ConfigSecretProviderFactory::class.java)

        /**
         * Return a new instance of [ConfigManager] that has been initialized with service provider implementations
         * defined by the given [config]. The resolving of providers happens lazily when they are accessed for the
         * first time. This simplifies the configuration of client modules which needs to contain properties for the
         * relevant providers only.
         */
        fun create(config: Config): ConfigManager {
            val managerConfig = config.getConfigOrEmpty(CONFIG_MANAGER_SECTION)

            val secretProviderSupplier: () -> ConfigSecretProvider = {
                SECRET_PROVIDER_LOADER.findProviderFactory(
                    managerConfig,
                    SECRET_PROVIDER_NAME_PROPERTY,
                    ConfigSecretProviderFactory::name
                ).createProvider(managerConfig)
            }

            val fileProviderSupplier: (ConfigSecretProvider) -> ConfigFileProvider = { secretProvider ->
                FILE_PROVIDER_LOADER.findProviderFactory(
                    managerConfig,
                    FILE_PROVIDER_NAME_PROPERTY,
                    ConfigFileProviderFactory::name
                ).createProvider(managerConfig, secretProvider)
            }

            return ConfigManager(
                config,
                fileProviderSupplier,
                secretProviderSupplier,
                managerConfig.getBooleanOrDefault(SECRET_FROM_CONFIG_PROPERTY, true)
            )
        }

        /**
         * Try to resolve the factory for the provider of the given type from the given [config]. Determine the name
         * of the factory implementation from the given [nameProperty]. Use the [nameExtractor] function to obtain
         * the factory names. Throw a [ConfigException] if the factory cannot be resolved, either due to missing
         * configuration or if the specified factory cannot be found on the classpath.
         */
        private inline fun <reified T> ServiceLoader<T>.findProviderFactory(
            config: Config,
            nameProperty: String,
            nameExtractor: (T) -> String
        ): T {
            val providerName = config.getStringOrNull(nameProperty)
                ?: throw ConfigException("Missing '$nameProperty' property.", null)

            return find { nameExtractor(it) == providerName }
                ?: throw ConfigException(
                    "Could not find ${T::class.simpleName} with name '$providerName' on classpath.",
                    null
                )
        }

        /**
         * Either return [context] if it is not *null* or the [EMPTY_CONTEXT].
         */
        private fun safeContext(context: Context?): Context = context ?: EMPTY_CONTEXT

        /**
         * Return the system's temporary directory.
         */
        private fun getTempDir(): File = File(System.getProperty("java.io.tmpdir"))
    }

    /** Stores the provider for secrets. */
    private val configSecretProvider by lazy { configSecretProviderSupplier() }

    /** Stores the provider for configuration files. */
    private val configFileProvider by lazy { configFileProviderSupplier(configSecretProvider) }

    /**
     * Ask the underlying [ConfigFileProvider] to resolve the given [context]. Throw a [ConfigException] if this fails.
     */
    fun resolveContext(context: Context?): Context =
        wrapExceptions { configFileProvider.resolveContext(safeContext(context)) }

    /**
     * Return an [InputStream] for reading the content of the configuration file at the given [path] in the given
     * [context]. Throw a [ConfigException] if the underlying [ConfigFileProvider] throws an exception.
     */
    fun getFile(context: Context?, path: Path): InputStream =
        wrapExceptions { configFileProvider.getFile(safeContext(context), path) }

    /**
     * Return the content of the configuration file under the given [path] in the given [context] as a string.
     * Throw a [ConfigException] if the underlying [ConfigFileProvider] throws an exception.
     */
    fun getFileAsString(context: Context?, path: Path): String {
        val configStream = getFile(context, path)

        return wrapExceptions {
            configStream.use { stream ->
                String(stream.readAllBytes())
            }
        }
    }

    /**
     * Download the configuration file at the given [path] in the given [context] to a directory. If a
     * [directory] is specified, the file is created there (the directory must exist); otherwise, the default temporary
     * directory is used for this purpose. If specified, use [targetName] as name for the new file. Otherwise,
     * derive the file name from the given [path] (by extracting the last path component). Return a [File] pointing to
     * the downloaded configuration data. Throw a [ConfigException] if the underlying [ConfigFileProvider] throws an
     * exception or the file could not be written.
     */
    fun downloadFile(context: Context?, path: Path, directory: File = getTempDir(), targetName: String? = null): File {
        val targetFile = File(directory, targetName ?: path.nameComponent)
        val configStream = getFile(context, path)

        return wrapExceptions {
            configStream.use { stream ->
                targetFile.outputStream().use { out ->
                    stream.copyTo(out)
                }
            }
            targetFile
        }
    }

    /**
     * Check whether a configuration file exists at the given [path] in the given [context]. Throw a
     * [ConfigException] if the underlying [ConfigFileProvider] throws an exception.
     */
    fun containsFile(context: Context?, path: Path): Boolean =
        wrapExceptions { configFileProvider.contains(safeContext(context), path) }

    /**
     * Return a [Set] with the [Path]s to configuration files that are located under the given [path] in the given
     * [context]. The provided [path] should point to a directory, so that it can contain files. Throw a
     * [ConfigException] if the underlying [ConfigFileProvider] throws an exception.
     */
    fun listFiles(context: Context?, path: Path): Set<Path> =
        wrapExceptions { configFileProvider.listFiles(safeContext(context), path) }

    /**
     * Return the value of the secret identified by the given [path]. Throw a [ConfigException] if the underlying
     * [ConfigSecretProvider] throws an exception.
     */
    fun getSecret(path: Path): String =
        wrapExceptions {
            if (allowSecretsFromConfig && config.hasPath(path.path)) {
                config.getString(path.path)
            } else {
                configSecretProvider.getSecret(path)
            }
        }

    /**
     * Return a [ConfigManager] instance that is derived from this object, but wraps the sub configuration at the
     * given [path]. The returned [ConfigManager] uses the same providers.
     */
    fun subConfig(path: Path): ConfigManager {
        if (!hasPath(path.path)) throw ConfigException("Non-existing path for subConfig: ${path.path}", null)

        return ConfigManager(
            config.getConfig(path.path),
            { configFileProvider },
            { configSecretProvider },
            allowSecretsFromConfig
        )
    }
}

/**
 * A specialized exception class for reporting errors related to the access of configuration data.
 */
class ConfigException(message: String, cause: Throwable?) : Exception(message, cause)

/**
 * Execute the given [block] and catch all exceptions it might throw. A caught exception is then wrapped in a
 * [ConfigException] which is rethrown.
 */
@Suppress("TooGenericExceptionCaught")
private fun <T> wrapExceptions(block: () -> T): T =
    try {
        block()
    } catch (e: ConfigException) {
        // Do not wrap ConfigExceptions.
        throw e
    } catch (e: Exception) {
        throw ConfigException("Exception from config provider", e)
    }
