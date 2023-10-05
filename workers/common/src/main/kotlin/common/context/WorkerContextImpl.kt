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

package org.ossreviewtoolkit.server.workers.common.context

import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Context
import org.ossreviewtoolkit.server.config.Path as ConfigPath
import org.ossreviewtoolkit.server.model.Hierarchy
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.secrets.Path
import org.ossreviewtoolkit.server.secrets.SecretStorage
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

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
    private val ortRunId: Long
) : WorkerContext {
    /** The object for accessing secrets. */
    private val secretStorage by lazy { SecretStorage.createStorage(configManager) }

    /** A cache for the secrets that have already been loaded. */
    private val secretsCache = ConcurrentHashMap<String, Deferred<String>>()

    /** A map to keep track on downloaded files, so that they can be removed on close. */
    private val downloadedFiles = ConcurrentHashMap<ConfigPath, Deferred<Result<File>>>()

    /**
     * A set to store the temporary directories created by this context that need to be cleaned up on closing. The
     * underlying concurrent map is used to guarantee thread-safe access.
     */
    private val tempDirectories = Collections.newSetFromMap(ConcurrentHashMap<File, Boolean>())

    override val ortRun: OrtRun by lazy {
        requireNotNull(ortRunRepository.get(ortRunId)) { "Could not resolve ORT run ID $ortRunId" }
    }

    override val hierarchy: Hierarchy by lazy {
        repositoryRepository.getHierarchy(ortRun.repositoryId)
    }

    override fun createTempDir(): File =
        createOrtTempDir(ortRunId.toString()).also(tempDirectories::add)

    override suspend fun resolveSecret(secret: Secret): String =
        singleTransform(secret, secretsCache, this::resolveSecret, ::extractSecretKey)

    override suspend fun resolveSecrets(vararg secrets: Secret): Map<Secret, String> {
        return parallelTransform(secrets.toList(), secretsCache, this::resolveSecret, ::extractSecretKey)
    }

    override suspend fun downloadConfigurationFile(path: ConfigPath): File =
        singleTransform(path, downloadedFiles, this::downloadConfigFile, ::identityKeyExtract).getOrThrow()

    override suspend fun downloadConfigurationFiles(paths: Collection<ConfigPath>): Map<ConfigPath, File> {
        val results = parallelTransform(paths, downloadedFiles, this::downloadConfigFile, ::identityKeyExtract)

        val failure = results.values.find { it.isFailure }
        return if (failure == null) {
            results.map { e -> e.key to e.value.getOrThrow() }.toMap()
        } else {
            throw failure.exceptionOrNull()!!
        }
    }

    override fun close() {
        val files = runBlocking { downloadedFiles.values.awaitAll() }.mapNotNull { it.getOrNull() }
        files.forEach { it.delete() }

        tempDirectories.forEach { it.safeDeleteRecursively(force = true) }
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
     * Download the configuration file identified by the given [path] from the current [Context] using the owned
     * configuration manager.
     */
    private fun downloadConfigFile(path: ConfigPath): Result<File> = runCatching {
        configManager.downloadFile(ortRun.resolvedJobConfigContext?.let(::Context), path)
    }
}

/**
 * A key extraction function for [Secret]s. As key for the given [secret] its path is used.
 */
private fun extractSecretKey(secret: Secret): String = secret.path

/**
 * An identity key extraction function that uses the given [t] as its own key. This is used if the cache for a
 * transformation stores the involved data objects as keys directly.
 */
private fun <T> identityKeyExtract(t: T): T = t
