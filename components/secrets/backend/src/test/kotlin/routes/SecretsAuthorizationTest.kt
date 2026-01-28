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

package org.eclipse.apoapsis.ortserver.components.secrets.routes

import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.secrets.PatchSecret
import org.eclipse.apoapsis.ortserver.components.secrets.PostSecret
import org.eclipse.apoapsis.ortserver.components.secrets.SecretService
import org.eclipse.apoapsis.ortserver.components.secrets.secretsRoutes
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

class SecretsAuthorizationTest : AbstractAuthorizationTest({
    var orgId = 0L
    var prodId = 0L
    var repoId = 0L
    lateinit var orgHierarchyId: CompoundHierarchyId
    lateinit var productHierarchyId: CompoundHierarchyId
    lateinit var repoHierarchyId: CompoundHierarchyId
    lateinit var repositoryService: RepositoryService
    lateinit var secretService: SecretService

    beforeEach {
        orgId = dbExtension.fixtures.organization.id
        prodId = dbExtension.fixtures.product.id
        repoId = dbExtension.fixtures.repository.id

        orgHierarchyId = CompoundHierarchyId.forOrganization(OrganizationId(orgId))
        productHierarchyId = CompoundHierarchyId.forProduct(
            OrganizationId(orgId),
            ProductId(prodId)
        )
        repoHierarchyId = CompoundHierarchyId.forRepository(
            OrganizationId(orgId),
            ProductId(prodId),
            RepositoryId(repoId)
        )

        repositoryService = RepositoryService(
            dbExtension.db,
            dbExtension.fixtures.ortRunRepository,
            dbExtension.fixtures.repositoryRepository,
            dbExtension.fixtures.analyzerJobRepository,
            dbExtension.fixtures.advisorJobRepository,
            dbExtension.fixtures.scannerJobRepository,
            dbExtension.fixtures.evaluatorJobRepository,
            dbExtension.fixtures.reporterJobRepository,
            dbExtension.fixtures.notifierJobRepository,
            mockk()
        )

        secretService = SecretService(
            dbExtension.db,
            dbExtension.fixtures.secretRepository,
            SecretStorage(SecretsProviderFactoryForTesting().createProvider())
        )
    }

    "GetAvailableSecretsByRepositoryId" should {
        "require RepositoryPermission.READ" {
            requestShouldRequireRole(
                routes = { secretsRoutes(repositoryService, secretService) },
                role = RepositoryRole.READER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = repoHierarchyId
            ) {
                get("/repositories/$repoId/secrets/availableSecrets")
            }
        }
    }

    "GetSecretByOrganizationIdAndName" should {
        "require OrganizationPermission.READ" {
            requestShouldRequireRole(
                routes = { secretsRoutes(repositoryService, secretService) },
                role = OrganizationRole.READER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = orgHierarchyId
            ) {
                get("/organizations/$orgId/secrets/name")
            }
        }
    }

    "GetSecretByProductIdAndName" should {
        "require ProductPermission.READ" {
            requestShouldRequireRole(
                routes = { secretsRoutes(repositoryService, secretService) },
                role = ProductRole.READER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = productHierarchyId
            ) {
                get("/products/$prodId/secrets/name")
            }
        }
    }

    "GetSecretByRepositoryIdAndName" should {
        "require RepositoryPermission.READ" {
            requestShouldRequireRole(
                routes = { secretsRoutes(repositoryService, secretService) },
                role = RepositoryRole.READER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = repoHierarchyId
            ) {
                get("/repositories/$repoId/secrets/name")
            }
        }
    }

    "GetSecretsByOrganizationId" should {
        "require OrganizationPermission.READ" {
            requestShouldRequireRole(
                routes = { secretsRoutes(repositoryService, secretService) },
                role = OrganizationRole.READER,
                hierarchyId = orgHierarchyId
            ) {
                get("/organizations/$orgId/secrets")
            }
        }
    }

    "GetSecretsByProductId" should {
        "require ProductPermission.READ" {
            requestShouldRequireRole(
                routes = { secretsRoutes(repositoryService, secretService) },
                role = ProductRole.READER,
                hierarchyId = productHierarchyId
            ) {
                get("/products/$prodId/secrets")
            }
        }
    }

    "GetSecretsByRepositoryId" should {
        "require RepositoryPermission.READ" {
            requestShouldRequireRole(
                routes = { secretsRoutes(repositoryService, secretService) },
                role = RepositoryRole.READER,
                hierarchyId = repoHierarchyId
            ) {
                get("/repositories/$repoId/secrets")
            }
        }
    }

    "PatchSecretByOrganizationIdAndName" should {
        "require OrganizationPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(repositoryService, secretService) },
                role = OrganizationRole.ADMIN,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = orgHierarchyId
            ) {
                val updateSecret = PatchSecret("value".asPresent(), "description".asPresent())
                patch("/organizations/$orgId/secrets/name") { setBody(updateSecret) }
            }
        }
    }

    "PatchSecretByProductIdAndName" should {
        "require ProductPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(repositoryService, secretService) },
                role = ProductRole.ADMIN,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = productHierarchyId
            ) {
                val updateSecret = PatchSecret("value".asPresent(), "description".asPresent())
                patch("/products/$prodId/secrets/name") { setBody(updateSecret) }
            }
        }
    }

    "PatchSecretByRepositoryIdAndName" should {
        "require RepositoryPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(repositoryService, secretService) },
                role = RepositoryRole.ADMIN,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = repoHierarchyId
            ) {
                val updateSecret = PatchSecret("value".asPresent(), "description".asPresent())
                patch("/repositories/$repoId/secrets/name") { setBody(updateSecret) }
            }
        }
    }

    "PostSecretForOrganization" should {
        "require OrganizationPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(repositoryService, secretService) },
                role = OrganizationRole.ADMIN,
                successStatus = HttpStatusCode.Created,
                hierarchyId = orgHierarchyId
            ) {
                val createSecret = PostSecret("name", "value", "description")
                post("/organizations/$orgId/secrets") { setBody(createSecret) }
            }
        }
    }

    "PostSecretForProduct" should {
        "require ProductPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(repositoryService, secretService) },
                role = ProductRole.ADMIN,
                successStatus = HttpStatusCode.Created,
                hierarchyId = productHierarchyId
            ) {
                val createSecret = PostSecret("name", "value", "description")
                post("/products/$prodId/secrets") { setBody(createSecret) }
            }
        }
    }

    "PostSecretForRepository" should {
        "require RepositoryPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(repositoryService, secretService) },
                role = RepositoryRole.ADMIN,
                successStatus = HttpStatusCode.Created,
                hierarchyId = repoHierarchyId
            ) {
                val createSecret = PostSecret("name", "value", "description")
                post("/repositories/$repoId/secrets") { setBody(createSecret) }
            }
        }
    }
})
