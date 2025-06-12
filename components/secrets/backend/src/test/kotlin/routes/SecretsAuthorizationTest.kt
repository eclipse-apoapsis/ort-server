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

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.authorization.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.secrets.CreateSecret
import org.eclipse.apoapsis.ortserver.components.secrets.UpdateSecret
import org.eclipse.apoapsis.ortserver.components.secrets.secretsRoutes
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.services.SecretService
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

class SecretsAuthorizationTest : AbstractAuthorizationTest({
    var orgId = 0L
    var prodId = 0L
    var repoId = 0L
    lateinit var secretService: SecretService

    beforeEach {
        orgId = dbExtension.fixtures.organization.id
        prodId = dbExtension.fixtures.product.id
        repoId = dbExtension.fixtures.repository.id

        authorizationService.ensureSuperuserAndSynchronizeRolesAndPermissions()

        secretService = SecretService(
            dbExtension.db,
            dbExtension.fixtures.secretRepository,
            dbExtension.fixtures.infrastructureServiceRepository,
            SecretStorage(SecretsProviderFactoryForTesting().createProvider())
        )
    }

    "DeleteSecretByOrganizationIdAndName" should {
        "require OrganizationPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = OrganizationPermission.WRITE_SECRETS.roleName(orgId),
                successStatus = HttpStatusCode.NotFound
            ) {
                delete("/organizations/$orgId/secrets/name")
            }
        }
    }

    "DeleteSecretByProductIdAndName" should {
        "require ProductPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = ProductPermission.WRITE_SECRETS.roleName(prodId),
                successStatus = HttpStatusCode.NotFound
            ) {
                delete("/products/$prodId/secrets/name")
            }
        }
    }

    "DeleteSecretByRepositoryIdAndName" should {
        "require RepositoryPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = RepositoryPermission.WRITE_SECRETS.roleName(repoId),
                successStatus = HttpStatusCode.NotFound
            ) {
                delete("/repositories/$repoId/secrets/name")
            }
        }
    }

    "GetSecretByOrganizationIdAndName" should {
        "require OrganizationPermission.READ" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = OrganizationPermission.READ.roleName(orgId),
                successStatus = HttpStatusCode.NotFound
            ) {
                get("/organizations/$orgId/secrets/name")
            }
        }
    }

    "GetSecretByProductIdAndName" should {
        "require ProductPermission.READ" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = ProductPermission.READ.roleName(prodId),
                successStatus = HttpStatusCode.NotFound
            ) {
                get("/products/$prodId/secrets/name")
            }
        }
    }

    "GetSecretByRepositoryIdAndName" should {
        "require RepositoryPermission.READ" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = RepositoryPermission.READ.roleName(repoId),
                successStatus = HttpStatusCode.NotFound
            ) {
                get("/repositories/$repoId/secrets/name")
            }
        }
    }

    "GetSecretsByOrganizationId" should {
        "require OrganizationPermission.READ" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = OrganizationPermission.READ.roleName(orgId)
            ) {
                get("/organizations/$orgId/secrets")
            }
        }
    }

    "GetSecretsByProductId" should {
        "require ProductPermission.READ" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = ProductPermission.READ.roleName(prodId)
            ) {
                get("/products/$prodId/secrets")
            }
        }
    }

    "GetSecretsByRepositoryId" should {
        "require RepositoryPermission.READ" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = RepositoryPermission.READ.roleName(repoId)
            ) {
                get("/repositories/$repoId/secrets")
            }
        }
    }

    "PatchSecretByOrganizationIdAndName" should {
        "require OrganizationPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = OrganizationPermission.WRITE_SECRETS.roleName(orgId),
                successStatus = HttpStatusCode.NotFound
            ) {
                val updateSecret = UpdateSecret("value".asPresent(), "description".asPresent())
                patch("/organizations/$orgId/secrets/name") { setBody(updateSecret) }
            }
        }
    }

    "PatchSecretByProductIdAndName" should {
        "require ProductPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = ProductPermission.WRITE_SECRETS.roleName(prodId),
                successStatus = HttpStatusCode.NotFound
            ) {
                val updateSecret = UpdateSecret("value".asPresent(), "description".asPresent())
                patch("/products/$prodId/secrets/name") { setBody(updateSecret) }
            }
        }
    }

    "PatchSecretByRepositoryIdAndName" should {
        "require RepositoryPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = RepositoryPermission.WRITE_SECRETS.roleName(repoId),
                successStatus = HttpStatusCode.NotFound
            ) {
                val updateSecret = UpdateSecret("value".asPresent(), "description".asPresent())
                patch("/repositories/$repoId/secrets/name") { setBody(updateSecret) }
            }
        }
    }

    "PostSecretForOrganization" should {
        "require OrganizationPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = OrganizationPermission.WRITE_SECRETS.roleName(orgId),
                successStatus = HttpStatusCode.Created
            ) {
                val createSecret = CreateSecret("name", "value", "description")
                post("/organizations/$orgId/secrets") { setBody(createSecret) }
            }
        }
    }

    "PostSecretForProduct" should {
        "require ProductPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = ProductPermission.WRITE_SECRETS.roleName(prodId),
                successStatus = HttpStatusCode.Created
            ) {
                val createSecret = CreateSecret("name", "value", "description")
                post("/products/$prodId/secrets") { setBody(createSecret) }
            }
        }
    }

    "PostSecretForRepository" should {
        "require RepositoryPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsRoutes(secretService) },
                role = RepositoryPermission.WRITE_SECRETS.roleName(repoId),
                successStatus = HttpStatusCode.Created
            ) {
                val createSecret = CreateSecret("name", "value", "description")
                post("/repositories/$repoId/secrets") { setBody(createSecret) }
            }
        }
    }
})
