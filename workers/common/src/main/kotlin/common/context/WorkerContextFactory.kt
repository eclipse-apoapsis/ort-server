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
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull

import org.ossreviewtoolkit.utils.common.EnvironmentVariableFilter

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
    companion object {
        /**
         * The name of the section in the worker configuration that defines general settings for ORT.
         */
        const val ORT_CONFIG_SECTION = "ort"

        /**
         * The name of the configuration property that defines the names of environment variables that are allowed to
         * be propagated to child processes. The value is a comma-separated list of environment variable names. If
         * it is defined, the allow list of ORT's [EnvironmentVariableFilter] class it initialized accordingly.
         */
        const val ENV_ALLOW_NAMES_PROPERTY = "environmentAllowedNames"

        /**
         * The name of the configuration property that defines substrings to be matched in environment variables that
         * prevent these variables from being propagated to child processes. The value is a comma-separated list of
         * substrings. If it is defined, the deny list of ORT's [EnvironmentVariableFilter] class it initialized
         * accordingly.
         */
        const val ENV_DENY_SUBSTRINGS_PROPERTY = "environmentDenySubstrings"

        /** The delimiter for splitting properties with multiple values. */
        private const val DELIMITER = ","

        /**
         * Split the given [value] into a list of strings.
         */
        private fun splitProperty(value: String): List<String> {
            return value.split(DELIMITER).map { it.trim() }
        }
    }

    /**
     * Return a [WorkerContext] for the given [ID of an ORT run][ortRunId]. The context is lazily initialized; so the
     * instance creation is not an expensive operation. When functionality is used, data may be loaded dynamically.
     */
    fun createContext(ortRunId: Long): WorkerContext {
        setUpOrtConfig()
        return WorkerContextImpl(configManager, ortRunRepository, repositoryRepository, ortRunId)
    }

    /**
     * Apply ORT-specific configuration settings as defined by the current [ConfigManager].
     */
    private fun setUpOrtConfig() {
        if (configManager.hasPath(ORT_CONFIG_SECTION)) {
            val ortConfig = configManager.subConfig(Path(ORT_CONFIG_SECTION))
            val envAllowNames = ortConfig.getStringOrNull(ENV_ALLOW_NAMES_PROPERTY)
            val envDenySubstrings = ortConfig.getStringOrNull(ENV_DENY_SUBSTRINGS_PROPERTY)

            if (envAllowNames != null && envDenySubstrings != null) {
                EnvironmentVariableFilter.reset(splitProperty(envDenySubstrings), splitProperty(envAllowNames))
            }
        }
    }
}
