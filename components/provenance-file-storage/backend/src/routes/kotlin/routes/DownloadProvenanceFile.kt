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

import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requireSuperuser
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ParsedProvenanceKey
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileStorageService
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileType
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.decodeProvenanceFileId
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.parseStorageKey
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.respondError
import org.eclipse.apoapsis.ortserver.storage.Key

internal fun Route.downloadProvenanceFile(
    provenanceFileStorageService: ProvenanceFileStorageService,
    type: ProvenanceFileType
) = get("admin/${type.pathSegment}/{id}/download", {
    operationId = "download${type.operationName}"
    summary = "Download a stored ${type.displayName}"
    description = "Download a ${type.displayName} stored for the provenance of a scanned package. The file is " +
            "returned as stored by the scanner, which is ${type.formatDescription}."
    tags = listOf("Admin")

    request {
        pathParameter<String>("id") {
            description = "The ID of the ${type.displayName}, as returned by the list endpoint."
            required = true
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success. The response body contains the ${type.displayName}."
            header<String>("Content-Type") {
                description = "The content type is set to '${type.contentType}'."
            }
            header<String>("Content-Disposition") {
                description = "Contains the suggested file name of the ${type.displayName}."
            }
        }

        HttpStatusCode.BadRequest to {
            description = "The ID is invalid."
        }

        HttpStatusCode.NotFound to {
            description = "The ${type.displayName} does not exist."
        }
    }
}, requireSuperuser()) {
    val id = call.requireParameter("id")
    val key = decodeProvenanceFileId(id)
        ?: return@get call.respondError(HttpStatusCode.BadRequest, "Invalid ${type.displayName} ID: '$id'.")

    val entry = provenanceFileStorageService.read(type, key) ?: return@get call.respondError(
        HttpStatusCode.NotFound,
        "The ${type.displayName} with ID '$id' does not exist."
    )

    entry.use {
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName,
                downloadFileName(type, key)
            ).toString()
        )

        call.respondOutputStream(type.contentType, contentLength = entry.length) {
            entry.data.copyTo(this)
        }
    }
}

/** A description of the format of files of this type. */
private val ProvenanceFileType.formatDescription: String
    get() = when (this) {
        ProvenanceFileType.FILE_ARCHIVE -> "a ZIP archive"
        ProvenanceFileType.FILE_LIST -> "a YAML file"
    }

/** A regular expression matching all characters that should not be used in file names. */
private val unsafeFileNameCharacters = Regex("[^A-Za-z0-9._-]+")

/**
 * Return a file name for downloading the file of the given [type] stored under the given [key]. The name is derived
 * from the provenance if the [key] can be parsed, and contains only characters that are safe to use in file names.
 */
internal fun downloadFileName(type: ProvenanceFileType, key: Key): String {
    val baseName = when (val parsedKey = parseStorageKey(key)) {
        is ParsedProvenanceKey.SourceArtifact -> "${parsedKey.url.lastPathSegment()}-${parsedKey.hashValue}"
        is ParsedProvenanceKey.Vcs -> "${parsedKey.url.lastPathSegment().removeSuffix(".git")}-${parsedKey.revision}"
        null -> type.pathSegment.removeSuffix("s")
    }

    val safeBaseName = baseName.replace(unsafeFileNameCharacters, "_").trim('_', '.').ifEmpty {
        type.pathSegment.removeSuffix("s")
    }

    return "$safeBaseName.${type.fileExtension}"
}

/** Return the last segment of the path of this URL, ignoring the query, the fragment, and trailing slashes. */
private fun String.lastPathSegment(): String =
    substringBefore('#').substringBefore('?').trimEnd('/').substringAfterLast('/')
