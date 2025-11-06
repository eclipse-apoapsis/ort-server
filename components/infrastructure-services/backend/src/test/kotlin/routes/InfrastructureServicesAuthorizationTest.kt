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

package org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServiceService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.PatchInfrastructureService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.PostInfrastructureService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.infrastructureServicesRoutes
import org.eclipse.apoapsis.ortserver.components.secrets.SecretService
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

class InfrastructureServicesAuthorizationTest : AbstractAuthorizationTest({
    var orgId = 0L
    var prodId = 0L
    var repoId = 0L
    lateinit var orgHierarchyId: CompoundHierarchyId
    lateinit var prodHierarchyId: CompoundHierarchyId
    lateinit var repoHierarchyId: CompoundHierarchyId
    lateinit var infrastructureServiceService: InfrastructureServiceService

    beforeEach {
        orgId = dbExtension.fixtures.organization.id
        prodId = dbExtension.fixtures.product.id
        repoId = dbExtension.fixtures.repository.id
        orgHierarchyId = CompoundHierarchyId.forOrganization(OrganizationId(orgId))
        prodHierarchyId = CompoundHierarchyId.forProduct(
            OrganizationId(orgId),
            ProductId(prodId)
        )
        repoHierarchyId = CompoundHierarchyId.forRepository(
            OrganizationId(orgId),
            ProductId(prodId),
            RepositoryId(repoId)
        )

        infrastructureServiceService = InfrastructureServiceService(
            dbExtension.db,
            SecretService(
                dbExtension.db,
                dbExtension.fixtures.secretRepository,
                SecretStorage(SecretsProviderFactoryForTesting().createProvider())
            )
        )
    }

    "DeleteOrganizationInfrastructureService" should {
        "require OrganizationPermission.WRITE" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = OrganizationRole.WRITER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = orgHierarchyId
            ) {
                delete("/organizations/$orgId/infrastructure-services/name")
            }
        }
    }

    "GetOrganizationInfrastructureService" should {
        "require OrganizationPermission.READ" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = OrganizationRole.READER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = orgHierarchyId
            ) {
                get("/organizations/$orgId/infrastructure-services/not-found")
            }
        }
    }

    "GetOrganizationInfrastructureServices" should {
        "require OrganizationPermission.READ" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = OrganizationRole.READER,
                hierarchyId = orgHierarchyId
            ) {
                get("/organizations/$orgId/infrastructure-services")
            }
        }
    }

    "PatchOrganizationInfrastructureService" should {
        "require OrganizationPermission.WRITE" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = OrganizationRole.WRITER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = orgHierarchyId
            ) {
                patch("/organizations/$orgId/infrastructure-services/name") {
                    setBody(
                        PatchInfrastructureService(
                            description = null.asPresent(),
                            url = "https://repo2.example.org/test2".asPresent()
                        )
                    )
                }
            }
        }
    }

    "PostOrganizationInfrastructureService" should {
        "require OrganizationPermission.WRITE" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = OrganizationRole.WRITER,
                successStatus = HttpStatusCode.InternalServerError,
                hierarchyId = orgHierarchyId
            ) {
                post("/organizations/$orgId/infrastructure-services") {
                    setBody(
                        PostInfrastructureService(
                            "testRepository",
                            "https://repo.example.org/test",
                            "test description",
                            "userSecret",
                            "passSecret"
                        )
                    )
                }
            }
        }
    }

    "DeleteProductInfrastructureService" should {
        "require ProductPermission.WRITE" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = ProductRole.WRITER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = prodHierarchyId
            ) {
                delete("/products/$prodId/infrastructure-services/name")
            }
        }
    }

    "GetProductInfrastructureService" should {
        "require ProductPermission.READ" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = ProductRole.READER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = prodHierarchyId
            ) {
                get("/products/$prodId/infrastructure-services/not-found")
            }
        }
    }

    "GetProductInfrastructureServices" should {
        "require ProductPermission.READ" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = ProductRole.READER,
                hierarchyId = prodHierarchyId
            ) {
                get("/products/$prodId/infrastructure-services")
            }
        }
    }

    "PatchProductInfrastructureService" should {
        "require ProductPermission.WRITE" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = ProductRole.WRITER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = prodHierarchyId
            ) {
                patch("/products/$prodId/infrastructure-services/name") {
                    setBody(
                        PatchInfrastructureService(
                            description = null.asPresent(),
                            url = "https://repo2.example.org/test2".asPresent()
                        )
                    )
                }
            }
        }
    }

    "PostProductInfrastructureService" should {
        "require ProductPermission.WRITE" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = ProductRole.WRITER,
                successStatus = HttpStatusCode.InternalServerError,
                hierarchyId = prodHierarchyId
            ) {
                post("/products/$prodId/infrastructure-services") {
                    setBody(
                        PostInfrastructureService(
                            "testRepository",
                            "https://repo.example.org/test",
                            "test description",
                            "userSecret",
                            "passSecret"
                        )
                    )
                }
            }
        }
    }

    "DeleteRepositoryInfrastructureService" should {
        "require RepositoryPermission.WRITE" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = repoHierarchyId
            ) {
                delete("/repositories/$repoId/infrastructure-services/name")
            }
        }
    }

    "GetRepositoryInfrastructureService" should {
        "require RepositoryPermission.READ" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = RepositoryRole.READER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = repoHierarchyId
            ) {
                get("/repositories/$repoId/infrastructure-services/not-found")
            }
        }
    }

    "GetRepositoryInfrastructureServices" should {
        "require RepositoryPermission.READ" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = RepositoryRole.READER,
                hierarchyId = repoHierarchyId
            ) {
                get("/repositories/$repoId/infrastructure-services")
            }
        }
    }

    "PatchRepositoryInfrastructureService" should {
        "require RepositoryPermission.WRITE" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = repoHierarchyId
            ) {
                patch("/repositories/$repoId/infrastructure-services/name") {
                    setBody(
                        PatchInfrastructureService(
                            description = null.asPresent(),
                            url = "https://repo2.example.org/test2".asPresent()
                        )
                    )
                }
            }
        }
    }

    "PostRepositoryInfrastructureService" should {
        "require RepositoryPermission.WRITE" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.InternalServerError,
                hierarchyId = repoHierarchyId
            ) {
                post("/repositories/$repoId/infrastructure-services") {
                    setBody(
                        PostInfrastructureService(
                            "testRepository",
                            "https://repo.example.org/test",
                            "test description",
                            "userSecret",
                            "passSecret"
                        )
                    )
                }
            }
        }
    }
})
