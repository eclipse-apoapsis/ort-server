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

package org.ossreviewtoolkit.server.services

import io.ktor.http.ContentType

import java.io.OutputStream

import org.ossreviewtoolkit.server.storage.Key
import org.ossreviewtoolkit.server.storage.Storage

/**
 * A service providing functionality related to accessing report files from a storage, so that they can be downloaded
 * from clients.
 */
class ReportStorageService(
    /** The [Storage] that contains the report files. */
    private val reportStorage: Storage
) {
    /**
     * Return a [ReportDownloadData] object for the report with the given [fileName] for the specified [runId]. Throw a
     * [ReportNotFoundException] if the report cannot be resolved.
     */
    fun fetchReport(runId: Long, fileName: String): ReportDownloadData {
        val key = generateKey(runId, fileName)
        if (!reportStorage.containsKey(key)) throw ReportNotFoundException(runId, fileName)

        val entry = reportStorage.read(key)

        val contentType = entry.contentType?.let(ContentType::parse) ?: ContentType.Application.OctetStream
        return ReportDownloadData(contentType) {
            entry.data.copyTo(this)
        }
    }
}

/**
 * A data class representing the result of a function that obtains report data from the configured storage. The
 * properties held by an instance can be used to stream the report data as response for a client request.
 */
data class ReportDownloadData(
    /** The content type of the report data. */
    val contentType: ContentType,

    /** A function to stream the represented data into an [OutputStream]. */
    val loader: suspend OutputStream.() -> Unit
)

/**
 * Class for an exception thrown by [ReportStorageService] if a requested report cannot be resolved.
 */
class ReportNotFoundException(runId: Long, fileName: String) :
    Exception("Could not resolve report '$fileName' for run $runId.")

/**
 * Generate the storage [Key] for the given combination of [runId] and [fileName].
 */
private fun generateKey(runId: Long, fileName: String): Key = Key("$runId|$fileName")
