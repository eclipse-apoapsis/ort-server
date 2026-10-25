/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.provenancefilestorage.routes

import io.github.smiley4.ktoropenapi.config.RequestConfig

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ParameterConversionException

import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileType

/** The name of the query parameter to request a refresh of the cached listing. */
internal const val REFRESH_PARAMETER = "refresh"

/** The segment of the URL path for files of this type. */
internal val ProvenanceFileType.pathSegment: String
    get() = when (this) {
        ProvenanceFileType.FILE_ARCHIVE -> "file-archives"
        ProvenanceFileType.FILE_LIST -> "file-lists"
    }

/** The name of this type to be used in operation IDs. */
internal val ProvenanceFileType.operationName: String
    get() = when (this) {
        ProvenanceFileType.FILE_ARCHIVE -> "FileArchive"
        ProvenanceFileType.FILE_LIST -> "FileList"
    }

/** A human-readable name of this type to be used in descriptions. */
internal val ProvenanceFileType.displayName: String
    get() = when (this) {
        ProvenanceFileType.FILE_ARCHIVE -> "file archive"
        ProvenanceFileType.FILE_LIST -> "file list"
    }

/** Document the query parameter to request a refresh of the cached listing. */
internal fun RequestConfig.refreshQueryParameter() {
    queryParameter<Boolean>(REFRESH_PARAMETER) {
        description = "If true, the storage is listed again instead of using the cached listing, which is " +
                "refreshed every few minutes."
        required = false
    }
}

/**
 * Return the value of the query parameter to request a refresh, defaulting to *false*. Throw a
 * [ParameterConversionException] if the value is not a valid Boolean.
 */
internal fun ApplicationCall.refreshParameter(): Boolean =
    parameters[REFRESH_PARAMETER]?.let { value ->
        value.lowercase().toBooleanStrictOrNull() ?: throw ParameterConversionException(REFRESH_PARAMETER, "Boolean")
    } ?: false
