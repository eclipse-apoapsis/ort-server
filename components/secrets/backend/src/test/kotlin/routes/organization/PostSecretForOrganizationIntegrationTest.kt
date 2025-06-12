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

package org.eclipse.apoapsis.ortserver.components.secrets.routes.organization

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
import io.ktor.server.response.respond

import org.eclipse.apoapsis.ortserver.components.secrets.CreateSecret
import org.eclipse.apoapsis.ortserver.components.secrets.Secret
import org.eclipse.apoapsis.ortserver.components.secrets.mapToApi
import org.eclipse.apoapsis.ortserver.components.secrets.secretsRoutes
import org.eclipse.apoapsis.ortserver.components.secrets.secretsValidations
import org.eclipse.apoapsis.ortserver.dao.UniqueConstraintException
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.services.SecretService
import org.eclipse.apoapsis.ortserver.shared.apimodel.ErrorResponse
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest
import org.eclipse.apoapsis.ortserver.shared.ktorutils.shouldHaveBody

class PostSecretForOrganizationIntegrationTest : AbstractIntegrationTest({
    var orgId = 0L
    lateinit var secretRepository: SecretRepository
    lateinit var secretService: SecretService

    beforeEach {
        orgId = dbExtension.fixtures.organization.id
        secretRepository = dbExtension.fixtures.secretRepository
        secretService = SecretService(
            dbExtension.db,
            dbExtension.fixtures.secretRepository,
            dbExtension.fixtures.infrastructureServiceRepository,
            SecretStorage(SecretsProviderFactoryForTesting().createProvider())
        )
    }

    "PostSecretForOrganization" should {
        "create a secret in the database" {
            integrationTestApplication(routes = { secretsRoutes(secretService) }) { client ->
                val secret = CreateSecret("name", "value", "description")

                val response = client.post("/organizations/$orgId/secrets") {
                    setBody(secret)
                }

                response shouldHaveStatus HttpStatusCode.Created
                response shouldHaveBody Secret(secret.name, secret.description)

                secretRepository.getByIdAndName(OrganizationId(orgId), secret.name)?.mapToApi() shouldBe
                        Secret(secret.name, secret.description)

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("organization_${orgId}_${secret.name}"))?.value shouldBe "value"
            }
        }

        "respond with 'Conflict' if the secret already exists" {
            integrationTestApplication(routes = { secretsRoutes(secretService) }) { client ->
                install(StatusPages) {
                    // TODO: This should use the same config as in core.
                    exception<UniqueConstraintException> { call, e ->
                        call.respond(
                            HttpStatusCode.Conflict,
                            ErrorResponse("The entity you tried to create already exists.", e.message)
                        )
                    }
                }

                val secret = CreateSecret("name", "value", "description")

                client.post("/organizations/$orgId/secrets") {
                    setBody(secret)
                } shouldHaveStatus HttpStatusCode.Created

                client.post("/organizations/$orgId/secrets") {
                    setBody(secret)
                } shouldHaveStatus HttpStatusCode.Conflict
            }
        }

        "respond with 'Bad Request' if the secret's name is invalid" {
            integrationTestApplication(
                routes = { secretsRoutes(secretService) },
                validations = { secretsValidations() }
            ) { client ->
                install(StatusPages) {
                    // TODO: This should use the same config as in core.
                    exception<RequestValidationException> { call, e ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Request validation has failed.", e.message)
                        )
                    }
                }

                val secret = CreateSecret(" New secret 6!", "value", "description")

                val response = client.post("/organizations/$orgId/secrets") {
                    setBody(secret)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for CreateSecret"

                secretRepository.getByIdAndName(OrganizationId(orgId), secret.name)?.mapToApi().shouldBeNull()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path("organization_${orgId}_${secret.name}"))?.value.shouldBeNull()
            }
        }
    }
})
