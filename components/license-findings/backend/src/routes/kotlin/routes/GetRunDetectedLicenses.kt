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

package org.eclipse.apoapsis.ortserver.components.licensefindings.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.licensefindings.DetectedLicense
import org.eclipse.apoapsis.ortserver.components.licensefindings.LicenseFindingService
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

internal fun Route.getRunDetectedLicenses(
    service: LicenseFindingService,
    ortRunRepository: OrtRunRepository
): Route =
    get("runs/{runId}/detected-licenses", {
        operationId = "getRunDetectedLicenses"
        summary = "Get detected licenses for an ORT run with package counts"
        tags = listOf("Runs")

        request {
            pathParameter<Long>("runId") {
                description = "The ID of the ORT run."
            }
            queryParameter<String>("license") {
                description = "Filter by license using a case-insensitive substring match."
            }
            standardListQueryParameters()
        }

        response {
            HttpStatusCode.OK to {
                description = "Success."
                jsonBody<PagedResponse<DetectedLicense>> {
                    example("Get detected licenses") {
                        value = PagedResponse(
                            data = listOf(
                                DetectedLicense("Apache-2.0", 3L),
                                DetectedLicense("MIT", 1L)
                            ),
                            pagination = PagingData(
                                limit = 20,
                                offset = 0,
                                totalCount = 2,
                                sortProperties = listOf(SortProperty("license", SortDirection.ASCENDING))
                            )
                        )
                    }
                }
            }
        }
    }, requireRunReadPermission(ortRunRepository)) {
        val runId = call.requireIdParameter("runId")

        ortRunRepository.get(runId) ?: return@get call.respond(HttpStatusCode.NotFound)

        val result = service.getDetectedLicensesForRun(
            ortRunId = runId,
            parameters = call.pagingOptions(SortProperty("license", SortDirection.ASCENDING)).mapToModel(),
            licenseFilter = call.parameters["license"]
        )

        call.respond(HttpStatusCode.OK, result.mapToApi { it })
    }
