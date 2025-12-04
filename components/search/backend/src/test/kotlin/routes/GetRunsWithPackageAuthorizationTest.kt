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

import io.mockk.coEvery
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.components.search.apimodel.RunWithPackage
import org.eclipse.apoapsis.ortserver.components.search.backend.SearchService
import org.eclipse.apoapsis.ortserver.components.search.searchRoutes
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

import ort.eclipse.apoapsis.ortserver.components.search.createRunWithPackage

class GetRunsWithPackageAuthorizationTest : AbstractAuthorizationTest({
    lateinit var searchService: SearchService
    lateinit var runWithPackage: RunWithPackage
    lateinit var hierarchyAuthorizationService: AuthorizationService

    beforeEach {
        hierarchyAuthorizationService = mockk {
            coEvery {
                filterHierarchyIds(any(), any(), any(), any(), any())
            } returns HierarchyFilter.WILDCARD
        }
        searchService = SearchService(dbExtension.db, hierarchyAuthorizationService)
        runWithPackage = createRunWithPackage(
            fixtures = dbExtension.fixtures,
            repoId = dbExtension.fixtures.repository.id
        )
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
            val orgHierarchyId = CompoundHierarchyId.forOrganization(
                OrganizationId(runWithPackage.organizationId)
            )

            requestShouldRequireRole(
                routes = { searchRoutes(searchService) },
                role = OrganizationRole.READER,
                hierarchyId = orgHierarchyId,
                successStatus = HttpStatusCode.OK
            ) {
                get("/search/package") {
                    parameter("identifier", runWithPackage.packageId)
                    parameter("organizationId", runWithPackage.organizationId.toString())
                }
            }
        }

        "require product permission when scoped to product" {
            val productHierarchyId = CompoundHierarchyId.forProduct(
                OrganizationId(runWithPackage.organizationId),
                ProductId(runWithPackage.productId)
            )

            requestShouldRequireRole(
                routes = { searchRoutes(searchService) },
                role = ProductRole.READER,
                hierarchyId = productHierarchyId,
                successStatus = HttpStatusCode.OK
            ) {
                get("/search/package") {
                    parameter("identifier", runWithPackage.packageId)
                    parameter("productId", runWithPackage.productId.toString())
                }
            }
        }

        "require repository permission when scoped to repository" {
            val repoHierarchyId = CompoundHierarchyId.forRepository(
                OrganizationId(runWithPackage.organizationId),
                ProductId(runWithPackage.productId),
                RepositoryId(runWithPackage.repositoryId)
            )

            requestShouldRequireRole(
                routes = { searchRoutes(searchService) },
                role = RepositoryRole.READER,
                hierarchyId = repoHierarchyId,
                successStatus = HttpStatusCode.OK
            ) {
                get("/search/package") {
                    parameter("identifier", runWithPackage.packageId)
                    parameter("repositoryId", runWithPackage.repositoryId.toString())
                }
            }
        }
    }
})
