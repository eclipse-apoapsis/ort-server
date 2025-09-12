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

package org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.organization

import io.github.smiley4.ktoropenapi.get

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.model.InfrastructureService as ModelInfrastructureService
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.services.InfrastructureServiceService
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToModel
import org.eclipse.apoapsis.ortserver.shared.apimodel.InfrastructureService
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.pagingOptions
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.standardListQueryParameters

internal fun Route.getInfrastructureServicesByOrganizationId(
    infrastructureServiceService: InfrastructureServiceService
) = get("/organizations/{organizationId}/infrastructure-services", {
    operationId = "GetInfrastructureServicesByOrganizationId"
    summary = "List all infrastructure services of an organization"
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The ID of an organization."
        }
        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<PagedResponse<InfrastructureService>> {
                example("List all infrastructure services for an organization") {
                    value = PagedResponse(
                        listOf(
                            InfrastructureService(
                                name = "Artifactory",
                                url = "https://artifactory.example.org/releases",
                                description = "Artifactory repository",
                                usernameSecretRef = "artifactoryUsername",
                                passwordSecretRef = "artifactoryPassword"
                            ),
                            InfrastructureService(
                                name = "GitHub",
                                url = "https://github.com",
                                description = "GitHub server",
                                usernameSecretRef = "gitHubUsername",
                                passwordSecretRef = "gitHubPassword"
                            )
                        ),
                        PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 2,
                            sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING)),
                        )
                    )
                }
            }
        }
    }
}) {
    requirePermission(OrganizationPermission.READ)

    val orgId = call.requireIdParameter("organizationId")
    val pagingOptions = call.pagingOptions(SortProperty("name", SortDirection.ASCENDING))

    val infrastructureServicesForOrganization =
        infrastructureServiceService.listForId(OrganizationId(orgId), pagingOptions.mapToModel())

    val pagedResponse = infrastructureServicesForOrganization.mapToApi(ModelInfrastructureService::mapToApi)

    call.respond(HttpStatusCode.OK, pagedResponse)
}
