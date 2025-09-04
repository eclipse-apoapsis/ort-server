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

package org.eclipse.apoapsis.ortserver.compositions.secretsroutes.routes

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.compositions.secretsroutes.SecretsRoutesIntegrationTest
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.shared.apimodel.ErrorResponse

class DeleteSecretByProductIdAndNameIntegrationTest : SecretsRoutesIntegrationTest({
    var prodId = 0L

    val secretErrorPath = "error-path"

    beforeEach {
        prodId = dbExtension.fixtures.product.id
    }

    "DeleteSecretByProductIdAndName" should {
        "delete a secret" {
            secretsRoutesTestApplication { client ->
                val secret = secretRepository.createProductSecret(prodId)

                client.delete("/products/$prodId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.NoContent

                secretRepository.listForId(ProductId(prodId)).data shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path)) should beNull()
            }
        }

        "respond with Conflict when secret is in use" {
            secretsRoutesTestApplication { client ->
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
                body.message shouldBe "The secret is still in use."
                body.cause shouldContain service.name
            }
        }

        "handle a failure from the SecretStorage" {
            secretsRoutesTestApplication { client ->
                val secret = secretRepository.createProductSecret(prodId, path = secretErrorPath)

                client.delete("/products/$prodId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.InternalServerError

                secretRepository.getByIdAndName(ProductId(prodId), secret.name) shouldBe secret
            }
        }
    }
})

fun SecretRepository.createProductSecret(
    prodId: Long,
    path: String = "path",
    name: String = "name",
    description: String = "description"
) = create(path, name, description, ProductId(prodId))
