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

import org.eclipse.apoapsis.ortserver.components.authorization.routes.AuthorizationChecker
import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.licensefindings.LicenseFindingDetail
import org.eclipse.apoapsis.ortserver.components.licensefindings.LicenseFindingService
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

internal fun Route.getRunDetectedLicenseFindings(
    service: LicenseFindingService,
    checker: AuthorizationChecker
) = get("licenses/{license}/packages/{identifier}/findings", {
    operationId = "getRunDetectedLicenseFindings"
    summary = "Get file-level findings for a specific license and package in an ORT run"
    tags = listOf("Runs")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }
        pathParameter<String>("license") {
            description = "The URL-encoded SPDX license identifier."
        }
        pathParameter<String>("identifier") {
            description = "The URL-encoded ORT identifier (e.g. Maven%3Acom.example%3Alib%3A1.0)."
        }
        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success."
            jsonBody<PagedResponse<LicenseFindingDetail>> {
                example("Get detected license findings") {
                    value = PagedResponse(
                        data = listOf(LicenseFindingDetail("LICENSE", 1, 10, 99f, "ScanCode 32.0.0")),
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
}, checker) {
    val result = service.getRunDetectedLicenseFindings(
        ortRunId = call.requireIdParameter("runId"),
        license = call.parameters["license"].orEmpty(),
        identifier = call.parameters["identifier"].orEmpty(),
        parameters = call.pagingOptions(SortProperty("path", SortDirection.ASCENDING)).mapToModel()
    )

    call.respond(HttpStatusCode.OK, result.mapToApi { it })
}
