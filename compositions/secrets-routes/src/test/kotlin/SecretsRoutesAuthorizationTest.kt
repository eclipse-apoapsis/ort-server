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

package org.eclipse.apoapsis.ortserver.compositions.secretsroutes

import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.authorization.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.DaoInfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.components.secrets.SecretService
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

class SecretsRoutesAuthorizationTest : AbstractAuthorizationTest({
    var orgId = 0L
    var prodId = 0L
    var repoId = 0L
    lateinit var infrastructureServiceRepository: InfrastructureServiceRepository
    lateinit var secretService: SecretService

    beforeEach {
        orgId = dbExtension.fixtures.organization.id
        prodId = dbExtension.fixtures.product.id
        repoId = dbExtension.fixtures.repository.id

        authorizationService.ensureSuperuserAndSynchronizeRolesAndPermissions()

        infrastructureServiceRepository = DaoInfrastructureServiceRepository(dbExtension.db)
        secretService = SecretService(
            dbExtension.db,
            dbExtension.fixtures.secretRepository,
            SecretStorage(SecretsProviderFactoryForTesting().createProvider())
        )
    }

    "DeleteSecretByOrganizationIdAndName" should {
        "require OrganizationPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsCompositionRoutes(infrastructureServiceRepository, secretService) },
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
                routes = { secretsCompositionRoutes(infrastructureServiceRepository, secretService) },
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
                routes = { secretsCompositionRoutes(infrastructureServiceRepository, secretService) },
                role = RepositoryPermission.WRITE_SECRETS.roleName(repoId),
                successStatus = HttpStatusCode.NotFound
            ) {
                delete("/repositories/$repoId/secrets/name")
            }
        }
    }
})
