/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import com.typesafe.config.ConfigFactory

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull

import org.ossreviewtoolkit.utils.common.EnvironmentVariableFilter

import org.slf4j.LoggerFactory

/**
 * A class that manages global ORT configuration settings that are independent of specific workers. It can be used to
 * configure related ORT components, so that workers can run in a properly set up environment.
 */
class WorkerOrtConfig private constructor(
    /** The object allowing access to the full server configuration. */
    val configManager: ConfigManager
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

        private val logger = LoggerFactory.getLogger(WorkerOrtConfig::class.java)

        /**
         * Create a new instance of [WorkerOrtConfig] that loads the configuration from the given [configManager].
         */
        fun create(configManager: ConfigManager): WorkerOrtConfig =
            WorkerOrtConfig(configManager)

        /**
         * Create a new instance of [WorkerOrtConfig]. Load the configuration from `application.properties` via the
         * default mechanism of [ConfigFactory].
         */
        fun create(): WorkerOrtConfig = create(ConfigManager.create(ConfigFactory.load()))

        /**
         * Split the given [value] into a list of strings or return *null* if it is undefined.
         */
        private fun splitProperty(value: String?): Collection<String>? = value?.split(DELIMITER)?.map { it.trim() }
    }

    /**
     * Configure the affected ORT components based on the settings in the configuration. After this call, workers
     * can run in a controlled environment.
     */
    fun setUpOrtEnvironment() {
        logger.info("Setting up ORT environment.")

        if (configManager.hasPath(ORT_CONFIG_SECTION)) {
            val ortConfig = configManager.subConfig(Path(ORT_CONFIG_SECTION))

            logger.info("Configuring EnvironmentVariableFilter.")
            val envAllowNames = splitProperty(
                ortConfig.getStringOrNull(ENV_ALLOW_NAMES_PROPERTY)
            ) ?: EnvironmentVariableFilter.DEFAULT_ALLOW_NAMES
            val envDenySubstrings = splitProperty(
                ortConfig.getStringOrNull(ENV_DENY_SUBSTRINGS_PROPERTY)
            ) ?: EnvironmentVariableFilter.DEFAULT_DENY_SUBSTRINGS

            EnvironmentVariableFilter.reset(envDenySubstrings, envAllowNames)
        }
    }
}
