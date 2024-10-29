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

package org.eclipse.apoapsis.ortserver.workers.reporter

import java.io.File
import java.nio.file.Files

import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.Storage

import org.slf4j.LoggerFactory

/**
 * A helper class used by the reporter to store the generated report files.
 */
class ReportStorage(
    /** The underlying [Storage] for persisting report files. */
    private val storage: Storage
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ReportStorage::class.java)

        /** The storage type used for reports. */
        const val STORAGE_TYPE = "reportStorage"

        /** The default content type to be used if the detection fails. */
        private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"

        /**
         * Try to determine the content type for the given [file].
         */
        internal fun guessContentType(file: File): String =
            runCatching { Files.probeContentType(file.toPath()) }.getOrNull() ?: DEFAULT_CONTENT_TYPE

        /**
         * Generate the storage [Key] for the report with the given [name] generated for the specified [runId].
         */
        private fun generateKey(runId: Long, name: String): Key =
            Key("$runId|$name")
    }

    /**
     * Store the given [files] in the associated [Storage] for the given [ORT run ID][runId]. The map with files has
     * the names to be used as keys and the corresponding report files as values.
     */
    fun storeReportFiles(runId: Long, files: Map<String, File>) {
        files.forEach { (name, file) ->
            val key = generateKey(runId, name)
            logger.info("Storing '{}' under key '{}'.", file.name, key.key)

            file.inputStream().use { stream ->
                storage.write(key, stream, file.length(), guessContentType(file))
            }
        }
    }
}
