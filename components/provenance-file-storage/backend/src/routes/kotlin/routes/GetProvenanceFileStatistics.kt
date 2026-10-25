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
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileKind
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileKindStatistics
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileStatistics
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileStorageService
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileType
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileUrlStatistics
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody

internal fun Route.getProvenanceFileStatistics(
    provenanceFileStorageService: ProvenanceFileStorageService,
    type: ProvenanceFileType
) = get("admin/${type.pathSegment}/statistics", {
    operationId = "get${type.operationName}Statistics"
    summary = "Get statistics about the stored ${type.displayName}s"
    description = "Get statistics about all ${type.displayName}s stored for the provenances of scanned packages. " +
            "The statistics are based on the same cached listing as the list of ${type.displayName}s."
    tags = listOf("Admin")

    request {
        refreshQueryParameter()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<ProvenanceFileStatistics> {
                example("Statistics about the stored ${type.displayName}s") {
                    value = ProvenanceFileStatistics(
                        snapshotTime = Instant.parse("2026-01-02T08:00:00Z"),
                        totalCount = 3,
                        totalSizeInBytes = 12288,
                        averageSizeInBytes = 4096,
                        largest = null,
                        oldestLastModified = Instant.parse("2026-01-01T10:00:00Z"),
                        newestLastModified = Instant.parse("2026-01-01T12:00:00Z"),
                        byKind = listOf(
                            ProvenanceFileKindStatistics(ProvenanceFileKind.SOURCE_ARTIFACT, 1, 2048),
                            ProvenanceFileKindStatistics(ProvenanceFileKind.VCS, 2, 10240)
                        ),
                        topUrls = listOf(
                            ProvenanceFileUrlStatistics("https://example.org/repo.git", 2, 10240),
                            ProvenanceFileUrlStatistics("https://example.org/artifact.zip", 1, 2048)
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

    call.respond(HttpStatusCode.OK, provenanceFileStorageService.statistics(type, refresh))
}
