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

package org.eclipse.apoapsis.ortserver.components.infrastructureservices

import io.ktor.client.HttpClient
import io.ktor.server.testing.ApplicationTestBuilder

import org.eclipse.apoapsis.ortserver.components.secrets.SecretService
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

/** An [AbstractIntegrationTest] pre-configured for testing the secrets routes. */
@Suppress("UnnecessaryAbstractClass")
abstract class InfrastructureServicesIntegrationTest(
    body: InfrastructureServicesIntegrationTest.() -> Unit
) : AbstractIntegrationTest({}) {
    lateinit var infrastructureServiceService: InfrastructureServiceService
    lateinit var secretService: SecretService

    var orgId = 0L
    var prodId = 0L
    var repoId = 0L
    lateinit var orgUserSecret: String
    lateinit var orgPassSecret: String
    lateinit var prodUserSecret: String
    lateinit var prodPassSecret: String
    lateinit var repoUserSecret: String
    lateinit var repoPassSecret: String

    init {
        beforeEach {
            secretService = SecretService(
                dbExtension.db,
                dbExtension.fixtures.secretRepository,
                SecretStorage(SecretsProviderFactoryForTesting().createProvider())
            )

            infrastructureServiceService = InfrastructureServiceService(dbExtension.db)

            orgId = dbExtension.fixtures.organization.id
            prodId = dbExtension.fixtures.product.id
            repoId = dbExtension.fixtures.repository.id
            orgUserSecret = secretService.createSecret(name = "user", "value", null, OrganizationId(orgId)).name
            orgPassSecret = secretService.createSecret(name = "pass", "value", null, OrganizationId(orgId)).name
            prodUserSecret = secretService.createSecret(name = "user", "value", null, ProductId(prodId)).name
            prodPassSecret = secretService.createSecret(name = "pass", "value", null, ProductId(prodId)).name
            repoUserSecret = secretService.createSecret(name = "user", "value", null, RepositoryId(repoId)).name
            repoPassSecret = secretService.createSecret(name = "pass", "value", null, RepositoryId(repoId)).name
        }

        body()
    }

    fun infrastructureServicesTestApplication(
        block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit
    ) = integrationTestApplication(
        routes = { infrastructureServicesRoutes(infrastructureServiceService) },
        validations = { infrastructureServicesValidations() },
        block = block
    )
}
