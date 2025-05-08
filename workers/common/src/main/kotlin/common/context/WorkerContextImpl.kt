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

package org.eclipse.apoapsis.ortserver.workers.common.context

import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path as ConfigPath
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.PluginConfig
import org.eclipse.apoapsis.ortserver.model.ProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.workers.common.auth.AuthenticationInfo
import org.eclipse.apoapsis.ortserver.workers.common.auth.AuthenticationListener
import org.eclipse.apoapsis.ortserver.workers.common.auth.CredentialResolverFun
import org.eclipse.apoapsis.ortserver.workers.common.auth.OrtServerAuthenticator
import org.eclipse.apoapsis.ortserver.workers.common.auth.credentialResolver
import org.eclipse.apoapsis.ortserver.workers.common.auth.undefinedCredentialResolver

import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.OrtAuthenticator
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(WorkerContextImpl::class.java)

/**
 * The internal default implementation of the [WorkerContext] interface.
 */
internal class WorkerContextImpl(
    /** The object allowing access to the application configuration. */
    override val configManager: ConfigManager,

    /** The repository for ORT run entities. */
    private val ortRunRepository: OrtRunRepository,

    /** The repository for repository entities. */
    private val repositoryRepository: RepositoryRepository,

    /** The ID of the current ORT run. */
    private val ortRunId: Long,
) : WorkerContext {
    /** The object for accessing secrets. */
    private val secretStorage by lazy { SecretStorage.createStorage(configManager) }

    /** A cache for the secrets that have already been loaded. */
    private val secretsCache = ConcurrentHashMap<String, Deferred<String>>()

    /** A cache for the configuration secrets that have already been loaded. */
    private val configSecretsCache = ConcurrentHashMap<String, Deferred<String>>()

    /** A cache for configuration files that have been downloaded. */
    private val downloadedFiles = ConcurrentHashMap<String, Deferred<Result<File>>>()

    /**
     * A set to store the temporary directories created by this context that need to be cleaned up on closing. The
     * underlying concurrent map is used to guarantee thread-safe access.
     */
    private val tempDirectories = Collections.newSetFromMap(ConcurrentHashMap<File, Boolean>())

    /**
     * The authenticator to be used for all requests during the lifetime of this context.
     */
    private val authenticator = OrtServerAuthenticator.install()

    /**
     * A reference to hold the function for resolving credentials. The function is updated whenever new authentication
     * information becomes available. Since this can happen from different threads, an atomic reference is used.
     */
    private val refCredentialResolverFun = AtomicReference(undefinedCredentialResolver)

    override val ortRun: OrtRun by lazy {
        requireNotNull(ortRunRepository.get(ortRunId)) { "Could not resolve ORT run ID $ortRunId" }
    }

    override val hierarchy: Hierarchy by lazy {
        repositoryRepository.getHierarchy(ortRun.repositoryId)
    }

    override val credentialResolverFun: CredentialResolverFun
        get() = { secret ->
            refCredentialResolverFun.get().invoke(secret)
        }

    override fun createTempDir(): File =
        createOrtTempDir(ortRunId.toString()).also(tempDirectories::add)

    /** Stores the resolved configuration context. */
    private val currentContext by lazy { ortRun.resolvedJobConfigContext?.let(::Context) }

    override suspend fun resolveSecret(secret: Secret): String =
        singleTransform(secret, secretsCache, this::resolveSecret, ::extractSecretKey)

    override suspend fun resolveSecrets(vararg secrets: Secret): Map<Secret, String> {
        return parallelTransform(secrets.toList(), secretsCache, this::resolveSecret, ::extractSecretKey)
    }

    override suspend fun resolvePluginConfigSecrets(
        config: Map<String, PluginConfig>?
    ): Map<String, PluginConfig> =
        config?.let { c ->
            val secrets = c.values.flatMap { it.secrets.values }
            val resolvedSecrets = parallelTransform(secrets, configSecretsCache, this::resolveConfigSecret) { it }

            c.mapValues { (_, pluginConfig) -> pluginConfig.resolveSecrets(resolvedSecrets) }
        }.orEmpty()

    override suspend fun resolveProviderPluginConfigSecrets(
        config: List<ProviderPluginConfiguration>?
    ): List<ProviderPluginConfiguration> =
        config?.let { c ->
            val secrets = c.flatMap { it.secrets.values }
            val resolvedSecrets = parallelTransform(secrets, configSecretsCache, this::resolveConfigSecret) { it }

            c.map { providerPluginConfig -> providerPluginConfig.resolveSecrets(resolvedSecrets) }
        }.orEmpty()

    override suspend fun downloadConfigurationFile(
        path: ConfigPath,
        directory: File,
        targetName: String?
    ): File =
        singleTransform(
            path,
            downloadedFiles,
            downloadConfigFile(directory, targetName),
            extractDownloadFileKey(directory.absolutePath, targetName)
        ).getOrThrow()

    override suspend fun downloadConfigurationFiles(
        paths: Collection<ConfigPath>,
        directory: File
    ): Map<ConfigPath, File> {
        val results = parallelTransform(
            paths,
            downloadedFiles,
            downloadConfigFile(directory, null),
            extractDownloadFileKey(directory.absolutePath, null)
        )

        return results.map { e -> e.key to e.value.getOrThrow() }.toMap()
    }

    override suspend fun downloadConfigurationDirectory(
        path: ConfigPath,
        targetDirectory: File
    ): Map<ConfigPath, File> {
        val containedFiles = configManager.listFiles(currentContext, path)

        logger.info("Downloading config directory '{}' to '{}'.", path.path, targetDirectory)
        logger.debug("The directory contains these files: {}.", containedFiles)

        return downloadConfigurationFiles(containedFiles, targetDirectory)
    }

    override suspend fun setupAuthentication(
        services: Collection<InfrastructureService>,
        listener: AuthenticationListener?
    ) {
        val serviceSecrets = services.flatMapTo(mutableSetOf()) { service ->
            listOf(service.usernameSecret, service.passwordSecret)
        }

        val resolvedSecrets = resolveSecrets(*serviceSecrets.toTypedArray()).mapKeys { it.key.path }
        val authInfo = AuthenticationInfo(resolvedSecrets, services.toList())

        authenticator.updateAuthenticationInfo(authInfo)
        authenticator.updateAuthenticationListener(listener)
        refCredentialResolverFun.set(credentialResolver(authInfo))
    }

    override fun close() {
        tempDirectories.forEach { it.safeDeleteRecursively() }

        OrtAuthenticator.uninstall()
    }

    /**
     * Helper function to transform a single [data] element using a [transformation function][transform]. Store the
     * transformed elements in the given [cache]. Since the key used by the cache is not necessarily the data item
     * itself, obtain it via the given [key extraction function][keyExtract].
     */
    private suspend fun <T, K, V> singleTransform(
        data: T,
        cache: ConcurrentMap<K, Deferred<V>>,
        transform: (K) -> V,
        keyExtract: (T) -> K
    ): V =
        transformAsync(data, cache, transform, keyExtract).await()

    /**
     * Helper function to transform multiple [data] elements in parallel using a [transformation function][transform].
     * Store the transformed elements in the given [cache]. Since the keys used by the cache are not necessarily the
     * data items themselves, obtain them via the given [key extraction function][keyExtract]. This function implements
     * the asynchronous mapping logic. It can be used to handle different kinds of data for which corresponding
     * transformation and key extraction functions have to be provided.
     */
    private suspend fun <T, K, V> parallelTransform(
        data: Collection<T>,
        cache: ConcurrentMap<K, Deferred<V>>,
        transform: (K) -> V,
        keyExtract: (T) -> K
    ): Map<T, V> {
        val results = data.map { transformAsync(it, cache, transform, keyExtract) }.awaitAll()

        return data.zip(results).toMap()
    }

    /**
     * Helper function to look up a [data] element from the given [cache] under the key obtained via the given
     * [key extraction function][keyExtract]. If the element is not contained in the cache, apply the given
     * [transformation function][transform] and store the result.
     */
    private suspend fun <T, K, V> transformAsync(
        data: T,
        cache: ConcurrentMap<K, Deferred<V>>,
        transform: (K) -> V,
        keyExtract: (T) -> K
    ): Deferred<V> =
        withContext(Dispatchers.IO) {
            val key = keyExtract(data)
            cache.getOrPut(key) { async { transform(key) } }
        }

    /**
     * Resolve the secret with the given [path] using the [SecretStorage] owned by this instance.
     */
    private fun resolveSecret(path: String): String =
        secretStorage.getSecret(Path(path)).value

    /**
     * Resolve the given [secret] from the configuration manager.
     */
    private fun resolveConfigSecret(secret: String): String =
        configManager.getSecret(ConfigPath(secret))

    /**
     * Return a function to download configuration files from the current [Context] into the given [directory],
     * optionally using the given [targetName].
     */
    private fun downloadConfigFile(directory: File, targetName: String?): (String) -> Result<File> =
        { key ->
            runCatching {
                val path = ConfigPath(key.substringBefore('|'))
                configManager.downloadFile(currentContext, path, directory, targetName)
            }
        }
}

/**
 * A key extraction function for [Secret]s. As key for the given [secret] its path is used.
 */
private fun extractSecretKey(secret: Secret): String = secret.path

/**
 * Return a key extraction function for configuration files that are downloaded to the given [directory] and optionally
 * renamed to the given [targetName]. The resulting function ensures that all relevant components are reflected in
 * the cache key.
 */
private fun extractDownloadFileKey(directory: String, targetName: String?): (ConfigPath) -> String = { path ->
    val name = targetName ?: path.nameComponent
    "${path.path}|$directory|$name"
}

/**
 * Return a [PluginConfig] whose secrets are resolved according to the given map with [secretValues].
 */
private fun PluginConfig.resolveSecrets(secretValues: Map<String, String>): PluginConfig {
    val resolvedSecrets = secrets.mapValues { e -> secretValues.getValue(e.value) }
    return copy(secrets = resolvedSecrets)
}

/**
 * Return a [ProviderPluginConfiguration] whose secrets are resolved according to the given map with [secretValues].
 */
private fun ProviderPluginConfiguration.resolveSecrets(secretValues: Map<String, String>): ProviderPluginConfiguration {
    val resolvedSecrets = secrets.mapValues { e -> secretValues.getValue(e.value) }
    return copy(secrets = resolvedSecrets)
}
