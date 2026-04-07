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

import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier
import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.licensefindings.LicenseFindingService
import org.eclipse.apoapsis.ortserver.components.licensefindings.PackageIdentifier
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

internal fun Route.getRunPackagesWithDetectedLicense(
    service: LicenseFindingService,
    ortRunRepository: OrtRunRepository
): Route =
    get("runs/{runId}/detected-licenses/{license}/packages", {
        operationId = "getRunPackagesWithDetectedLicense"
        summary = "Get packages containing a specific detected license for an ORT run"
        tags = listOf("Runs")

        request {
            pathParameter<Long>("runId") {
                description = "The ID of the ORT run."
            }
            pathParameter<String>("license") {
                description = "The URL-encoded SPDX license identifier (e.g. Apache-2.0, GPL-2.0-or-later)."
            }
            queryParameter<String>("identifier") {
                description = "Filter by ORT identifier using a case-insensitive substring match."
            }
            queryParameter<String>("purl") {
                description = "Filter by PURL using a case-insensitive substring match."
            }
            standardListQueryParameters()
        }

        response {
            HttpStatusCode.OK to {
                description = "Success."
                jsonBody<PagedResponse<PackageIdentifier>> {
                    example("Get packages with detected license") {
                        value = PagedResponse(
                            data = listOf(
                                PackageIdentifier(
                                    Identifier("Maven", "com.example", "lib", "1.0"),
                                    "pkg:maven/com.example/lib@1.0"
                                )
                            ),
                            pagination = PagingData(
                                limit = 20,
                                offset = 0,
                                totalCount = 1,
                                sortProperties = listOf(SortProperty("identifier", SortDirection.ASCENDING))
                            )
                        )
                    }
                }
            }
        }
    }, requireRunReadPermission(ortRunRepository)) {
        val result = service.getPackagesWithDetectedLicenseForRun(
            ortRunId = call.requireIdParameter("runId"),
            license = call.parameters["license"].orEmpty(),
            parameters = call.pagingOptions(SortProperty("identifier", SortDirection.ASCENDING)).mapToModel(),
            identifierFilter = call.parameters["identifier"],
            purlFilter = call.parameters["purl"]
        )

        call.respond(HttpStatusCode.OK, result.mapToApi { it })
    }
