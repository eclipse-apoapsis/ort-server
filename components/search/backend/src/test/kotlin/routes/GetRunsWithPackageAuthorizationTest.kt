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

package ort.eclipse.apoapsis.ortserver.components.search.routes

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.permissions.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.search.apimodel.RunWithPackage
import org.eclipse.apoapsis.ortserver.components.search.backend.SearchService
import org.eclipse.apoapsis.ortserver.components.search.searchRoutes
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

import ort.eclipse.apoapsis.ortserver.components.search.createRunWithPackage

class GetRunsWithPackageAuthorizationTest : AbstractAuthorizationTest({
    lateinit var searchService: SearchService
    lateinit var runWithPackage: RunWithPackage

    beforeEach {
        searchService = SearchService(dbExtension.db)
        runWithPackage = createRunWithPackage(
            fixtures = dbExtension.fixtures,
            repoId = dbExtension.fixtures.repository.id
        )
        authorizationService.ensureSuperuserAndSynchronizeRolesAndPermissions()
    }

    "GetRunsWithPackage" should {
        "require superuser role for unscoped searches" {
            requestShouldRequireSuperuser(
                routes = { searchRoutes(searchService) },
                successStatus = HttpStatusCode.OK
            ) {
                get("/search/package") {
                    parameter("identifier", runWithPackage.packageId)
                }
            }
        }

        "require organization permission when scoped to organization" {
            val organizationRole = OrganizationPermission.READ.roleName(runWithPackage.organizationId)

            requestShouldRequireRole(
                routes = { searchRoutes(searchService) },
                role = organizationRole,
                successStatus = HttpStatusCode.OK
            ) {
                get("/search/package") {
                    parameter("identifier", runWithPackage.packageId)
                    parameter("organizationId", runWithPackage.organizationId.toString())
                }
            }
        }

        "require product permission when scoped to product" {
            val productRole = ProductPermission.READ.roleName(runWithPackage.productId)

            requestShouldRequireRole(
                routes = { searchRoutes(searchService) },
                role = productRole,
                successStatus = HttpStatusCode.OK
            ) {
                get("/search/package") {
                    parameter("identifier", runWithPackage.packageId)
                    parameter("organizationId", runWithPackage.organizationId.toString())
                    parameter("productId", runWithPackage.productId.toString())
                }
            }
        }

        "require repository permission when scoped to repository" {
            val repositoryRole = RepositoryPermission.READ.roleName(runWithPackage.repositoryId)

            requestShouldRequireRole(
                routes = { searchRoutes(searchService) },
                role = repositoryRole,
                successStatus = HttpStatusCode.OK
            ) {
                get("/search/package") {
                    parameter("identifier", runWithPackage.packageId)
                    parameter("organizationId", runWithPackage.organizationId.toString())
                    parameter("productId", runWithPackage.productId.toString())
                    parameter("repositoryId", runWithPackage.repositoryId.toString())
                }
            }
        }
    }
})
