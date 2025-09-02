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

package org.eclipse.apoapsis.ortserver.components.secrets.routes.organization

import io.github.smiley4.ktoropenapi.get

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.components.secrets.Secret
import org.eclipse.apoapsis.ortserver.components.secrets.mapToApi
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.Secret as ModelSecret
import org.eclipse.apoapsis.ortserver.services.SecretService
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

internal fun Route.getSecretsByOrganizationId(secretService: SecretService) =
    get("/organizations/{organizationId}/secrets", {
        operationId = "GetSecretsByOrganizationId"
        summary = "Get all secrets of an organization"
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

                jsonBody<PagedResponse<Secret>> {
                    example("Get all secrets of an organization") {
                        value = PagedResponse(
                            listOf(
                                Secret(name = "token_npm_repo_1", description = "Access token for NPM Repo 1"),
                                Secret(name = "token_maven_repo_1", description = "Access token for Maven Repo 1")
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

        val secretsForOrganization = secretService.listForId(OrganizationId(orgId), pagingOptions.mapToModel())

        val pagedResponse = secretsForOrganization.mapToApi(ModelSecret::mapToApi)

        call.respond(HttpStatusCode.OK, pagedResponse)
    }
