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

package org.eclipse.apoapsis.ortserver.components.secrets.routes.product

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe

import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.secrets.Secret
import org.eclipse.apoapsis.ortserver.components.secrets.UpdateSecret
import org.eclipse.apoapsis.ortserver.components.secrets.mapToApi
import org.eclipse.apoapsis.ortserver.components.secrets.routes.createProductSecret
import org.eclipse.apoapsis.ortserver.components.secrets.secretsRoutes
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.services.SecretService
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest
import org.eclipse.apoapsis.ortserver.shared.ktorutils.shouldHaveBody

class PatchSecretByProductIdAndNameIntegrationTest : AbstractIntegrationTest({
    var prodId = 0L
    lateinit var secretRepository: SecretRepository
    lateinit var secretService: SecretService

    val secretErrorPath = "error-path"

    beforeEach {
        prodId = dbExtension.fixtures.product.id
        secretRepository = dbExtension.fixtures.secretRepository
        secretService = SecretService(
            dbExtension.db,
            dbExtension.fixtures.secretRepository,
            dbExtension.fixtures.infrastructureServiceRepository,
            SecretStorage(SecretsProviderFactoryForTesting().createProvider(secretErrorPath))
        )
    }

    "PatchSecretByProductIdAndName" should {
        "update a secret's metadata" {
            integrationTestApplication(routes = { secretsRoutes(secretService) }) { client ->
                val secret = secretRepository.createProductSecret(prodId)

                val updatedDescription = "updated description"
                val updateSecret = UpdateSecret("value".asPresent(), description = updatedDescription.asPresent())

                val response = client.patch("/products/$prodId/secrets/${secret.name}") {
                    setBody(updateSecret)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Secret(secret.name, updatedDescription)

                secretRepository.getByIdAndName(ProductId(prodId), secret.name)?.mapToApi() shouldBe
                        Secret(secret.name, updatedDescription)
            }
        }

        "update a secret's value" {
            integrationTestApplication(routes = { secretsRoutes(secretService) }) { client ->
                val secret = secretRepository.createProductSecret(prodId)

                val updateSecret = UpdateSecret("value".asPresent(), "description".asPresent())
                val response = client.patch("/products/$prodId/secrets/${secret.name}") {
                    setBody(updateSecret)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody secret.mapToApi()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path))?.value shouldBe "value"
            }
        }

        "handle a failure from the SecretStorage" {
            integrationTestApplication(routes = { secretsRoutes(secretService) }) { client ->
                val secret = secretRepository.createProductSecret(prodId, path = secretErrorPath)

                val updateSecret = UpdateSecret("value".asPresent(), "newDesc".asPresent())
                client.patch("/products/$prodId/secrets/${secret.name}") {
                    setBody(updateSecret)
                } shouldHaveStatus HttpStatusCode.InternalServerError

                secretRepository.getByIdAndName(ProductId(prodId), secret.name) shouldBe secret
            }
        }
    }
})
