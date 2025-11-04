/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.search.routes

import io.github.smiley4.ktoropenapi.get

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.permissions.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.requirePermission
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.requireSuperuser
import org.eclipse.apoapsis.ortserver.components.search.apimodel.RunWithPackage
import org.eclipse.apoapsis.ortserver.components.search.backend.SearchService
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

@Suppress("LongMethod")
internal fun Route.getRunsWithPackage(searchService: SearchService) =
    get("/search/package", {
        operationId = "getRunsWithPackage"
        summary = "Return ORT runs containing a package, possibly scoped by organization, product, and repository"
        tags = listOf("Search")

        request {
            queryParameter<String>("identifier") {
                description = "The package identifier to search for. Also RegEx supported."
                required = true
            }
            queryParameter<Long?>("organizationId") {
                description = "Optional organization ID to filter the search."
            }
            queryParameter<Long?>("productId") {
                description = "Optional product ID to filter the search."
            }
            queryParameter<Long?>("repositoryId") {
                description = "Optional repository ID to filter the search."
            }
        }

        response {
            HttpStatusCode.OK to {
                description = "Success"
                jsonBody<List<RunWithPackage>> {
                    example("Package Search Result") {
                        value = listOf(
                            RunWithPackage(
                                organizationId = 1L,
                                productId = 2L,
                                repositoryId = 3L,
                                ortRunId = 42L,
                                revision = "a1b2c3d4",
                                createdAt = Clock.System.now(),
                                packageId = "Maven:foo:bar:1.0.0"
                            ),
                            RunWithPackage(
                                organizationId = 1L,
                                productId = 3L,
                                repositoryId = 7L,
                                ortRunId = 120L,
                                revision = "a1b2c3d4",
                                createdAt = Clock.System.now(),
                                packageId = "Maven:foo:bar:1.0.0"
                            )
                        )
                    }
                }
            }
        }
    }) {
        val identifierParam = call.requireParameter("identifier")
        val organizationIdParam = call.request.queryParameters["organizationId"]?.toLongOrNull()
        val productIdParam = call.request.queryParameters["productId"]?.toLongOrNull()
        val repositoryIdParam = call.request.queryParameters["repositoryId"]?.toLongOrNull()

        if (repositoryIdParam != null && (productIdParam == null || organizationIdParam == null)) {
            return@get call.respond(
                HttpStatusCode.BadRequest,
                "A repository ID requires a product and an organization ID."
            )
        }
        if (productIdParam != null && organizationIdParam == null) {
            return@get call.respond(
                HttpStatusCode.BadRequest,
                "A product ID requires an organization ID."
            )
        }

        if (repositoryIdParam != null) {
            requirePermission(RepositoryPermission.READ.roleName(repositoryIdParam))
        } else if (productIdParam != null) {
            requirePermission(ProductPermission.READ.roleName(productIdParam))
        } else if (organizationIdParam != null) {
            requirePermission(OrganizationPermission.READ.roleName(organizationIdParam))
        } else {
            requireSuperuser()
        }

        val ortRuns = searchService.findOrtRunsByPackage(
            identifier = identifierParam,
            organizationId = organizationIdParam,
            productId = productIdParam,
            repositoryId = repositoryIdParam
        )
        call.respond(HttpStatusCode.OK, ortRuns)
    }
