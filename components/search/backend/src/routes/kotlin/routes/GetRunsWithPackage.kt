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

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier
import org.eclipse.apoapsis.ortserver.components.authorization.routes.OrtServerPrincipal.Companion.requirePrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.search.apimodel.RunWithPackage
import org.eclipse.apoapsis.ortserver.components.search.backend.SearchService
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody

@Suppress("LongMethod")
internal fun Route.getRunsWithPackage(searchService: SearchService) =
    get("/search/package", {
        operationId = "getRunsWithPackage"
        summary = "Return ORT runs containing a package, possibly scoped by organization, product, or repository"
        tags = listOf("Search")

        request {
            queryParameter<String?>("identifier") {
                description = "The package identifier to search for in ORT format (type:namespace:name:version). " +
                    "Supports regular expressions. Mutually exclusive with 'purl'."
            }
            queryParameter<String?>("purl") {
                description = "The package URL (PURL) to search for. Any curations for PURLs are taken into account. " +
                    "Supports regular expressions. Mutually exclusive with 'identifier'."
            }
            queryParameter<Long?>("organizationId") {
                description = "Optional organization ID to filter the search. " +
                    "Cannot be combined with 'productId' or 'repositoryId'."
            }
            queryParameter<Long?>("productId") {
                description = "Optional product ID to filter the search. " +
                    "Cannot be combined with 'organizationId' or 'repositoryId'."
            }
            queryParameter<Long?>("repositoryId") {
                description = "Optional repository ID to filter the search. " +
                    "Cannot be combined with 'organizationId' or 'productId'."
            }
        }

        response {
            HttpStatusCode.OK to {
                description = "Success"
                jsonBody<List<RunWithPackage>> {
                    example("Identifier Search Result") {
                        value = listOf(
                            RunWithPackage(
                                organizationId = 1L,
                                productId = 2L,
                                repositoryId = 3L,
                                ortRunId = 1042L,
                                ortRunIndex = 42L,
                                revision = "a1b2c3d4",
                                createdAt = Clock.System.now(),
                                packageId = Identifier(
                                    type = "Maven",
                                    namespace = "foo",
                                    name = "bar",
                                    version = "1.0.0"
                                ),
                                purl = null
                            )
                        )
                    }
                    example("PURL Search Result") {
                        value = listOf(
                            RunWithPackage(
                                organizationId = 1L,
                                productId = 2L,
                                repositoryId = 3L,
                                ortRunId = 1042L,
                                ortRunIndex = 42L,
                                revision = "a1b2c3d4",
                                createdAt = Clock.System.now(),
                                packageId = null,
                                purl = "pkg:maven/foo/bar@1.0.0"
                            )
                        )
                    }
                }
            }
            HttpStatusCode.BadRequest to {
                description = "If more than one scope parameter (organizationId, productId, repositoryId) is " +
                    "specified, or if neither/both 'identifier' and 'purl' are provided."
            }
        }
    }, requireScopedReadPermission()) {
        val identifierParam = call.request.queryParameters["identifier"]
        val purlParam = call.request.queryParameters["purl"]

        if ((identifierParam != null) == (purlParam != null)) {
            throw QueryParametersException(
                "Exactly one of 'identifier' or 'purl' must be provided."
            )
        }

        // parseScope() throws QueryParametersException if multiple scope parameters are provided
        val scope = call.parseScope()
        val userId = requirePrincipal().username

        val ortRuns = searchService.findOrtRunsByPackage(
            identifier = identifierParam,
            purl = purlParam,
            userId = userId,
            scope = scope
        )
        call.respond(HttpStatusCode.OK, ortRuns)
    }
