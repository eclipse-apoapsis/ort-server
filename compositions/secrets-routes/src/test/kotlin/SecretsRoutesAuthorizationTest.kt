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

import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServiceService
import org.eclipse.apoapsis.ortserver.components.secrets.SecretService
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

class SecretsRoutesAuthorizationTest : AbstractAuthorizationTest({
    var orgId = 0L
    var prodId = 0L
    var repoId = 0L
    lateinit var orgHierarchyId: CompoundHierarchyId
    lateinit var prodHierarchyId: CompoundHierarchyId
    lateinit var repoHierarchyId: CompoundHierarchyId
    lateinit var infrastructureServiceService: InfrastructureServiceService
    lateinit var secretService: SecretService

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

        secretService = SecretService(
            dbExtension.db,
            dbExtension.fixtures.secretRepository,
            SecretStorage(SecretsProviderFactoryForTesting().createProvider())
        )
        infrastructureServiceService = InfrastructureServiceService(
            dbExtension.db
        )
    }

    "DeleteOrganizationSecret" should {
        "require OrganizationPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsCompositionRoutes(infrastructureServiceService, secretService) },
                role = OrganizationRole.ADMIN,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = orgHierarchyId
            ) {
                delete("/organizations/$orgId/secrets/name")
            }
        }
    }

    "DeleteProductSecret" should {
        "require ProductPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsCompositionRoutes(infrastructureServiceService, secretService) },
                role = ProductRole.ADMIN,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = prodHierarchyId
            ) {
                delete("/products/$prodId/secrets/name")
            }
        }
    }

    "DeleteRepositorySecret" should {
        "require RepositoryPermission.WRITE_SECRETS" {
            requestShouldRequireRole(
                routes = { secretsCompositionRoutes(infrastructureServiceService, secretService) },
                role = RepositoryRole.ADMIN,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = repoHierarchyId
            ) {
                delete("/repositories/$repoId/secrets/name")
            }
        }
    }
})
