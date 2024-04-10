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

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository

/**
 * A factory class for creating [WorkerContext] instances.
 *
 * In addition to creating context objects, the class is also responsible for setting up some ORT-specific
 * configuration options. This makes sure that all workers have this configuration properly set.
 */
class WorkerContextFactory(
    /** The application configuration. */
    private val configManager: ConfigManager,

    /** The repository for ORT run entities. */
    private val ortRunRepository: OrtRunRepository,

    /** The repository for repository entities. */
    private val repositoryRepository: RepositoryRepository
) {
    /**
     * Return a [WorkerContext] for the given [ID of an ORT run][ortRunId]. The context is lazily initialized; so the
     * instance creation is not an expensive operation. When functionality is used, data may be loaded dynamically.
     */
    fun createContext(ortRunId: Long): WorkerContext {
        val ortConfig = WorkerOrtConfig.create(configManager)
        ortConfig.setUpOrtEnvironment()

        return WorkerContextImpl(configManager, ortRunRepository, repositoryRepository, ortRunId)
    }
}
