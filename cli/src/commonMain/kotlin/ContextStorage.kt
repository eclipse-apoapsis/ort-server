/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.cli

import com.charleskorn.kaml.Yaml

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

import org.eclipse.apoapsis.ortserver.cli.utils.configDir
import org.eclipse.apoapsis.ortserver.cli.utils.exists
import org.eclipse.apoapsis.ortserver.cli.utils.toSource
import org.eclipse.apoapsis.ortserver.cli.utils.write

private const val CONTEXT_FILE_NAME = "context.yml"

/**
 * Data class to store contextual information about the CLI's current state.
 */
@Serializable
internal data class ContextData(
    val run: RunData? = null,
)

/**
 * Data class to store contextual information about ORT runs.
 */
@Serializable
internal data class RunData(
    /* The ID of the latest run started by the CLI. */
    val latestId: Long? = null
)

/**
 * Object to manage the storage and retrieval of contextual information.
 */
internal object ContextStorage {
    private val contextFile = configDir.resolve(CONTEXT_FILE_NAME)
    private var storage: ContextData = ContextData()
    private var yaml = Yaml()

    init {
        if (contextFile.exists()) {
            storage = yaml.decodeFromSource(contextFile.toSource())
        }
    }

    fun get() = storage

    fun saveLatestRunId(runId: Long) {
        val runData = storage.run ?: RunData()

        saveContext(storage.copy(run = runData.copy(latestId = runId)))
    }

    private fun saveContext(context: ContextData) {
        storage = context

        saveToFile()
    }

    private fun saveToFile() = contextFile.write(yaml.encodeToString(storage))
}
