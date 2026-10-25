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

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requireSuperuser
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFile
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileKind
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileStorageService
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileType
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToModel
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.pagingOptions
import org.eclipse.apoapsis.ortserver.shared.ktorutils.standardListQueryParameters

/** The name of the query parameter to filter the files. */
private const val FILTER_PARAMETER = "filter"

internal fun Route.getProvenanceFiles(
    provenanceFileStorageService: ProvenanceFileStorageService,
    type: ProvenanceFileType
) = get("admin/${type.pathSegment}", {
    operationId = "get${type.operationName}s"
    summary = "List all stored ${type.displayName}s"
    description = "List the ${type.displayName}s stored for the provenances of scanned packages. Supported sort " +
            "fields are 'url', 'sizeInBytes', and 'lastModified'. As listing a storage can be expensive, the listing " +
            "is cached for a few minutes."
    tags = listOf("Admin")

    request {
        standardListQueryParameters()
        queryParameter<String>(FILTER_PARAMETER) {
            description = "Only return files whose URL, revision, hash, or storage key contains this string, " +
                    "ignoring case."
            required = false
        }
        refreshQueryParameter()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<PagedResponse<ProvenanceFile>> {
                example("List the stored ${type.displayName}s") {
                    value = PagedResponse(
                        listOf(
                            ProvenanceFile(
                                id = "dmNzfEdpdHxodHRwczovL2V4YW1wbGUub3JnL3JlcG8uZ2l0fDAxMjM0NTY3ODk",
                                key = "vcs|Git|https://example.org/repo.git|0123456789",
                                kind = ProvenanceFileKind.VCS,
                                url = "https://example.org/repo.git",
                                vcsType = "Git",
                                revision = "0123456789",
                                hashValue = null,
                                hashAlgorithm = null,
                                sizeInBytes = 4096,
                                lastModified = Instant.parse("2026-01-01T12:00:00Z")
                            )
                        ),
                        PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 1,
                            sortProperties = listOf(SortProperty("url", SortDirection.ASCENDING))
                        )
                    )
                }
            }
        }

        HttpStatusCode.BadRequest to {
            description = "Invalid query parameters."
        }
    }
}, requireSuperuser()) {
    val refresh = call.refreshParameter()
    val pagingOptions = call.pagingOptions(SortProperty("url", SortDirection.ASCENDING))
    val filter = call.parameters[FILTER_PARAMETER]

    val files = provenanceFileStorageService.list(type, filter, pagingOptions.mapToModel(), refresh)

    call.respond(HttpStatusCode.OK, files.mapToApi { it })
}
