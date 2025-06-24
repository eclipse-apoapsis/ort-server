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
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.components.secrets.routes.createProductSecret
import org.eclipse.apoapsis.ortserver.components.secrets.secretsRoutes
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.services.ReferencedEntityException
import org.eclipse.apoapsis.ortserver.services.SecretService
import org.eclipse.apoapsis.ortserver.shared.apimodel.ErrorResponse
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

class DeleteSecretByProductIdAndNameIntegrationTest : AbstractIntegrationTest({
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

    "DeleteSecretByProductIdAndName" should {
        "delete a secret" {
            integrationTestApplication(routes = { secretsRoutes(secretService) }) { client ->
                val secret = secretRepository.createProductSecret(prodId)

                client.delete("/products/$prodId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.NoContent

                secretRepository.listForId(ProductId(prodId)).data shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path)) should beNull()
            }
        }

        "respond with Conflict when secret is in use" {
            integrationTestApplication(routes = { secretsRoutes(secretService) }) { client ->
                install(StatusPages) {
                    // TODO: This should use the same config as in core.
                    exception<ReferencedEntityException> { call, e ->
                        call.respond(
                            HttpStatusCode.Conflict,
                            ErrorResponse("The entity you tried to delete is in use.", e.message)
                        )
                    }
                }

                val userSecret = secretRepository.createProductSecret(prodId, path = "user", name = "user")
                val passSecret = secretRepository.createProductSecret(prodId, path = "pass", name = "pass")

                val service = dbExtension.fixtures.infrastructureServiceRepository.create(
                    name = "testService",
                    url = "http://repo1.example.org/obsolete",
                    description = "good bye, cruel world",
                    usernameSecret = userSecret,
                    passwordSecret = passSecret,
                    credentialsTypes = EnumSet.of(CredentialsType.NETRC_FILE),
                    ProductId(prodId)
                )

                val response = client.delete("/products/$prodId/secrets/${userSecret.name}")
                response shouldHaveStatus HttpStatusCode.Conflict

                val body = response.body<ErrorResponse>()
                body.message shouldBe "The entity you tried to delete is in use."
                body.cause shouldContain service.name
            }
        }

        "handle a failure from the SecretStorage" {
            integrationTestApplication(routes = { secretsRoutes(secretService) }) { client ->
                val secret = secretRepository.createProductSecret(prodId, path = secretErrorPath)

                client.delete("/products/$prodId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.InternalServerError

                secretRepository.getByIdAndName(ProductId(prodId), secret.name) shouldBe secret
            }
        }
    }
})
