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
import io.ktor.server.plugins.statuspages.StatusPages

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.compositions.secretsroutes.SecretsRoutesIntegrationTest
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.services.ReferencedEntityException
import org.eclipse.apoapsis.ortserver.shared.apimodel.ErrorResponse
import org.eclipse.apoapsis.ortserver.shared.ktorutils.respondError

class DeleteSecretByOrganizationIdAndNameIntegrationTest : SecretsRoutesIntegrationTest({
    var orgId = 0L

    beforeEach {
        orgId = dbExtension.fixtures.organization.id
    }

    "DeleteSecretByOrganizationIdAndName" should {
        "delete a secret" {
            secretsRoutesTestApplication { client ->
                val secret = secretRepository.createOrganizationSecret(orgId)

                client.delete("/organizations/$orgId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.NoContent

                secretRepository.listForId(OrganizationId(orgId)).data shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path)) should beNull()
            }
        }

        "respond with 'Conflict' when secret is in use" {
            secretsRoutesTestApplication { client ->
                install(StatusPages) {
                    // TODO: This should use the same config as in core.
                    exception<ReferencedEntityException> { call, e ->
                        call.respondError(
                            HttpStatusCode.Conflict,
                            message = "The entity you tried to delete is in use.",
                            cause = e.message
                        )
                    }
                }

                val userSecret = secretRepository.createOrganizationSecret(orgId, path = "user", name = "user")
                val passSecret = secretRepository.createOrganizationSecret(orgId, path = "pass", name = "pass")

                val service = dbExtension.fixtures.infrastructureServiceRepository.create(
                    name = "testService",
                    url = "http://repo1.example.org/obsolete",
                    description = "good bye, cruel world",
                    usernameSecret = userSecret,
                    passwordSecret = passSecret,
                    credentialsTypes = EnumSet.of(CredentialsType.NETRC_FILE),
                    OrganizationId(orgId)
                )

                val response = client.delete("/organizations/$orgId/secrets/${userSecret.name}")
                response shouldHaveStatus HttpStatusCode.Conflict

                val body = response.body<ErrorResponse>()
                body.message shouldBe "The entity you tried to delete is in use."
                body.cause shouldContain service.name
            }
        }

        "handle a failure from the SecretStorage" {
            secretsRoutesTestApplication { client ->
                val secret = secretRepository.createOrganizationSecret(orgId, path = secretErrorPath)

                client.delete("/organizations/$orgId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.InternalServerError

                secretRepository.getByIdAndName(OrganizationId(orgId), secret.name) shouldBe secret
            }
        }
    }
})

fun SecretRepository.createOrganizationSecret(
    orgId: Long,
    path: String = "path",
    name: String = "name",
    description: String = "description"
) = create(path, name, description, OrganizationId(orgId))
