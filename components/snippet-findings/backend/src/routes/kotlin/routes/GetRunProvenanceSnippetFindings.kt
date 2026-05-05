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

package org.eclipse.apoapsis.ortserver.components.snippetfindings.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.snippetfindings.SnippetFinding
import org.eclipse.apoapsis.ortserver.components.snippetfindings.SnippetFindingService
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToModel
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.pagingOptions
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.standardListQueryParameters

internal fun Route.getRunProvenanceSnippetFindings(
    service: SnippetFindingService,
    ortRunRepository: OrtRunRepository
): Route =
    get("runs/{runId}/snippet-findings/provenances/{provenanceId}/findings", {
        operationId = "getRunProvenanceSnippetFindings"
        summary = "Get snippet findings for a specific package provenance in an ORT run"
        tags = listOf("Runs")

        request {
            pathParameter<Long>("runId") {
                description = "The ID of the ORT run."
            }
            pathParameter<Long>("provenanceId") {
                description = "The ID of the package provenance."
            }
            standardListQueryParameters()
        }

        response {
            HttpStatusCode.OK to {
                description = "Success."
                jsonBody<PagedResponse<SnippetFinding>> {
                    example("Get snippet findings for a provenance") {
                        value = PagedResponse(
                            data = listOf(
                                SnippetFinding(
                                    id = 1,
                                    path = "src/main/App.kt",
                                    startLine = 12,
                                    endLine = 18,
                                    snippetCount = 2
                                )
                            ),
                            pagination = PagingData(
                                limit = 20,
                                offset = 0,
                                totalCount = 1,
                                sortProperties = listOf(SortProperty("path", SortDirection.ASCENDING))
                            )
                        )
                    }
                }
            }
        }
    }, requireRunReadPermission(ortRunRepository)) {
        val runId = call.requireIdParameter("runId")
        val provenanceId = call.requireIdParameter("provenanceId")

        ortRunRepository.get(runId) ?: return@get call.respond(HttpStatusCode.NotFound)

        if (!service.hasProvenanceForRun(runId, provenanceId)) {
            return@get call.respond(HttpStatusCode.NotFound)
        }

        val result = service.getSnippetFindingsForRun(
            ortRunId = runId,
            provenanceId = provenanceId,
            parameters = call.pagingOptions(SortProperty("path", SortDirection.ASCENDING)).mapToModel()
        )

        call.respond(HttpStatusCode.OK, result.mapToApi { it })
    }
