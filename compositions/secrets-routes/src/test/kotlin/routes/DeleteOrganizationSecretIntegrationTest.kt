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
import io.kotest.matchers.collections.beEmpty
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
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.shared.apimodel.ErrorResponse

class DeleteOrganizationSecretIntegrationTest : SecretsRoutesIntegrationTest({
    var orgId = 0L
    var prodId = 0L

    beforeEach {
        orgId = dbExtension.fixtures.organization.id
        prodId = dbExtension.fixtures.product.id
    }

    "DeleteOrganizationSecret" should {
        "delete a secret" {
            secretsRoutesTestApplication { client ->
                val secret = secretRepository.createOrganizationSecret(orgId)

                client.delete("/organizations/$orgId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.NoContent

                secretRepository.listForId(OrganizationId(orgId)).data should beEmpty()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path)) should beNull()
            }
        }

        "respond with 'Conflict' when secret is in use" {
            secretsRoutesTestApplication { client ->
                val userSecret = secretRepository.createOrganizationSecret(orgId, path = "user", name = "user").name
                val passSecret = secretRepository.createOrganizationSecret(orgId, path = "pass", name = "pass").name

                val service = infrastructureServiceService.createForId(
                    OrganizationId(orgId),
                    name = "testService",
                    url = "http://repo1.example.org/obsolete",
                    description = "good bye, cruel world",
                    usernameSecretRef = userSecret,
                    passwordSecretRef = passSecret,
                    credentialsTypes = EnumSet.of(CredentialsType.NETRC_FILE)
                )

                val response = client.delete("/organizations/$orgId/secrets/$userSecret")
                response shouldHaveStatus HttpStatusCode.Conflict

                val body = response.body<ErrorResponse>()
                body.message shouldBe "The secret is still in use."
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

        "not block deletion when a same-named secret at a different hierarchy level is referenced" {
            secretsRoutesTestApplication { client ->
                val secretName = "sharedName"
                val orgSecret = secretRepository.createOrganizationSecret(
                    orgId,
                    path = "org-path",
                    name = secretName
                )
                secretRepository.createProductSecret(prodId, path = "product-path", name = secretName)

                infrastructureServiceService.createForId(
                    ProductId(prodId),
                    name = "productService",
                    url = "http://example.org/service",
                    description = null,
                    usernameSecretRef = secretName,
                    passwordSecretRef = secretName,
                    credentialsTypes = EnumSet.of(CredentialsType.NETRC_FILE)
                )

                client.delete("/organizations/$orgId/secrets/$secretName") shouldHaveStatus
                        HttpStatusCode.NoContent

                secretRepository.getByIdAndName(OrganizationId(orgId), secretName) shouldBe null
                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(orgSecret.path)) should beNull()
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
