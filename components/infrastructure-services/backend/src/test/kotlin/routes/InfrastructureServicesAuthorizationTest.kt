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

import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServiceService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.PatchInfrastructureService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.PostInfrastructureService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.infrastructureServicesRoutes
import org.eclipse.apoapsis.ortserver.components.secrets.SecretService
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

class InfrastructureServicesAuthorizationTest : AbstractAuthorizationTest({
    var orgId = 0L
    var repoId = 0L
    lateinit var infrastructureServiceService: InfrastructureServiceService

    beforeEach {
        orgId = dbExtension.fixtures.organization.id
        repoId = dbExtension.fixtures.repository.id

        authorizationService.ensureSuperuserAndSynchronizeRolesAndPermissions()

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
                role = OrganizationPermission.WRITE.roleName(orgId),
                successStatus = HttpStatusCode.NotFound
            ) {
                delete("/organizations/$orgId/infrastructure-services/name")
            }
        }
    }

    "GetOrganizationInfrastructureService" should {
        "require OrganizationPermission.READ" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = OrganizationPermission.READ.roleName(orgId),
                successStatus = HttpStatusCode.NotFound
            ) {
                get("/organizations/$orgId/infrastructure-services/not-found")
            }
        }
    }

    "GetOrganizationInfrastructureServices" should {
        "require OrganizationPermission.READ" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = OrganizationPermission.READ.roleName(orgId),
            ) {
                get("/organizations/$orgId/infrastructure-services")
            }
        }
    }

    "PatchOrganizationInfrastructureService" should {
        "require OrganizationPermission.WRITE" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = OrganizationPermission.WRITE.roleName(orgId),
                successStatus = HttpStatusCode.NotFound
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
                role = OrganizationPermission.WRITE.roleName(orgId),
                successStatus = HttpStatusCode.InternalServerError
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

    "DeleteRepositoryInfrastructureService" should {
        "require RepositoryPermission.WRITE" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = RepositoryPermission.WRITE.roleName(repoId),
                successStatus = HttpStatusCode.NotFound
            ) {
                delete("/repositories/$repoId/infrastructure-services/name")
            }
        }
    }

    "GetRepositoryInfrastructureService" should {
        "require RepositoryPermission.READ" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = RepositoryPermission.READ.roleName(repoId),
                successStatus = HttpStatusCode.NotFound
            ) {
                get("/repositories/$repoId/infrastructure-services/not-found")
            }
        }
    }

    "GetRepositoryInfrastructureServices" should {
        "require RepositoryPermission.READ" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = RepositoryPermission.READ.roleName(repoId)
            ) {
                get("/repositories/$repoId/infrastructure-services")
            }
        }
    }

    "PatchRepositoryInfrastructureService" should {
        "require RepositoryPermission.WRITE" {
            requestShouldRequireRole(
                routes = { infrastructureServicesRoutes(infrastructureServiceService) },
                role = RepositoryPermission.WRITE.roleName(repoId),
                successStatus = HttpStatusCode.NotFound
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
                role = RepositoryPermission.WRITE.roleName(repoId),
                successStatus = HttpStatusCode.InternalServerError
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
