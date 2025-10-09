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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages

import org.eclipse.apoapsis.ortserver.components.secrets.PostSecret
import org.eclipse.apoapsis.ortserver.components.secrets.Secret
import org.eclipse.apoapsis.ortserver.components.secrets.SecretsIntegrationTest
import org.eclipse.apoapsis.ortserver.components.secrets.mapToApi
import org.eclipse.apoapsis.ortserver.dao.UniqueConstraintException
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.shared.apimodel.ErrorResponse
import org.eclipse.apoapsis.ortserver.shared.ktorutils.respondError
import org.eclipse.apoapsis.ortserver.shared.ktorutils.shouldHaveBody

class PostProductSecretIntegrationTest : SecretsIntegrationTest({
    var prodId = 0L

    beforeEach {
        prodId = dbExtension.fixtures.product.id
    }

    "PostProductSecret" should {
        "create a secret in the database" {
            secretsTestApplication { client ->
                val secret = PostSecret("name", "value", "description")

                val response = client.post("/products/$prodId/secrets") {
                    setBody(secret)
                }

                response shouldHaveStatus HttpStatusCode.Created
                response shouldHaveBody Secret(secret.name, secret.description)

                secretRepository.getByIdAndName(ProductId(prodId), secret.name)?.mapToApi() shouldBe
                        Secret(secret.name, secret.description)

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("product_${prodId}_${secret.name}"))?.value shouldBe "value"
            }
        }

        "respond with CONFLICT if the secret already exists" {
            secretsTestApplication { client ->
                install(StatusPages) {
                    // TODO: This should use the same config as in core.
                    exception<UniqueConstraintException> { call, e ->
                        call.respondError(
                            HttpStatusCode.Conflict,
                            message = "The entity you tried to create already exists.",
                            cause = e.message
                        )
                    }
                }

                val secret = PostSecret("name", "value", "description")

                client.post("/products/$prodId/secrets") {
                    setBody(secret)
                } shouldHaveStatus HttpStatusCode.Created

                client.post("/products/$prodId/secrets") {
                    setBody(secret)
                } shouldHaveStatus HttpStatusCode.Conflict
            }
        }

        "respond with 'Bad Request' if the secret's name is invalid" {
            secretsTestApplication { client ->
                install(StatusPages) {
                    // TODO: This should use the same config as in core.
                    exception<RequestValidationException> { call, e ->
                        call.respondError(
                            HttpStatusCode.BadRequest,
                            message = "Request validation has failed.",
                            cause = e.message
                        )
                    }
                }

                val secret = PostSecret(" New secret 6!", "value", "description")

                val response = client.post("/products/$prodId/secrets") {
                    setBody(secret)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for PostSecret"

                secretRepository.getByIdAndName(ProductId(prodId), secret.name)?.mapToApi().shouldBeNull()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("product_${prodId}_${secret.name}"))?.value shouldBe null
            }
        }
    }
})
