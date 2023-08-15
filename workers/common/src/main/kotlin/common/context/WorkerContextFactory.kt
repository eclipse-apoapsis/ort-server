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

import java.util.concurrent.ConcurrentHashMap

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.model.Hierarchy
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.secrets.Path
import org.ossreviewtoolkit.server.secrets.SecretStorage

/**
 * A factory class for creating [WorkerContext] instances.
 */
class WorkerContextFactory(
    /** The application configuration. */
    private val configManager: ConfigManager,

    /** The repository for ORT run entities. */
    private val ortRunRepository: OrtRunRepository,

    /** The repository for repository entities. */
    private val repositoryRepository: RepositoryRepository
) {
    /** The object for accessing secrets. */
    private val secretStorage by lazy { SecretStorage.createStorage(configManager) }

    /**
     * Return a [WorkerContext] for the given [ID of an ORT run][ortRunId]. The context is lazily initialized; so the
     * instance creation is not an expensive operation. When functionality is used, data may be loaded dynamically.
     */
    fun createContext(ortRunId: Long): WorkerContext {
        val secretsCache = ConcurrentHashMap<String, Deferred<String>>()

        return object : WorkerContext {
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

            /**
             * Resolve the given [secret] asynchronously making use of the cache with secrets.
             */
            private suspend fun resolveSecretAsync(secret: Secret): Deferred<String> =
                withContext(Dispatchers.IO) {
                    secretsCache.getOrPut(secret.path) {
                        async { secretStorage.getSecret(Path(secret.path)).value }
                    }
                }
        }
    }
}
