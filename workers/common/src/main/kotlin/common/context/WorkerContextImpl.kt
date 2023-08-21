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
import java.util.concurrent.ConcurrentHashMap

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

/**
 * The internal default implementation of the [WorkerContext] interface.
 */
internal class WorkerContextImpl(
    /** The object allowing access to the application configuration. */
    private val configManager: ConfigManager,

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

    override val ortRun: OrtRun by lazy {
        requireNotNull(ortRunRepository.get(ortRunId)) { "Could not resolve ORT run ID $ortRunId" }
    }

    override val hierarchy: Hierarchy by lazy {
        repositoryRepository.getHierarchy(ortRun.repositoryId)
    }

    override suspend fun resolveSecret(secret: Secret): String =
        resolveSecretAsync(secret).await()

    override suspend fun resolveSecrets(vararg secrets: Secret): Map<Secret, String> {
        val deferredValues = secrets.map { resolveSecretAsync(it) }

        return secrets.zip(deferredValues.awaitAll()).toMap()
    }

    override suspend fun downloadConfigurationFile(path: ConfigPath): File =
        downloadFileAsync(path).await().getOrThrow()

    override suspend fun downloadConfigurationFiles(paths: Collection<ConfigPath>): Map<ConfigPath, File> {
        val results = paths.map { downloadFileAsync(it) }.awaitAll()

        val (success, failure) = results.partition { it.isSuccess }
        return success.takeIf { failure.isEmpty() }?.let { files ->
            paths.zip(files.map { it.getOrThrow() })
        }?.toMap() ?: throw failure.first().exceptionOrNull()!!
    }

    override fun close() {
        val files = runBlocking { downloadedFiles.values.awaitAll() }.mapNotNull { it.getOrNull() }
        files.forEach { it.delete() }
    }

    /**
     * Resolve the given [secret] asynchronously making use of the cache with secrets.
     */
    private suspend fun resolveSecretAsync(secret: Secret): Deferred<String> =
        withContext(Dispatchers.IO) {
            secretsCache.getOrPut(secret.path) {
                async { secretStorage.getSecret(Path(secret.path)).value }
            }
        }

    /**
     * Download the configuration file defined by the given [path] asynchronously making use of the cache for
     * already downloaded files.
     */
    private suspend fun downloadFileAsync(path: ConfigPath): Deferred<Result<File>> =
        withContext(Dispatchers.IO) {
            downloadedFiles.getOrPut(path) {
                async {
                    runCatching {
                        configManager.downloadFile(ortRun.resolvedJobConfigContext?.let(::Context), path)
                    }
                }
            }
        }
}
