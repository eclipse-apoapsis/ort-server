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
import io.kotest.matchers.shouldBe

import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.secrets.Secret
import org.eclipse.apoapsis.ortserver.components.secrets.SecretsIntegrationTest
import org.eclipse.apoapsis.ortserver.components.secrets.UpdateSecret
import org.eclipse.apoapsis.ortserver.components.secrets.mapToApi
import org.eclipse.apoapsis.ortserver.components.secrets.routes.createOrganizationSecret
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.ktorutils.shouldHaveBody

class PatchSecretByOrganizationIdAndNameIntegrationTest : SecretsIntegrationTest({
    var orgId = 0L

    val secretErrorPath = "error-path"

    beforeEach {
        orgId = dbExtension.fixtures.organization.id
    }

    "PatchSecretByOrganizationIdAndName" should {
        "update a secret's metadata" {
            secretsTestApplication { client ->
                val secret = secretRepository.createOrganizationSecret(orgId)

                val updatedDescription = "updated description"
                val updateSecret = UpdateSecret("value".asPresent(), description = updatedDescription.asPresent())

                val response = client.patch("/organizations/$orgId/secrets/${secret.name}") {
                    setBody(updateSecret)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Secret(secret.name, updatedDescription)

                secretRepository.getByIdAndName(OrganizationId(orgId), secret.name)
                    ?.mapToApi() shouldBe Secret(secret.name, updatedDescription)
            }
        }

        "update a secret's value" {
            secretsTestApplication { client ->
                val secret = secretRepository.createOrganizationSecret(orgId)

                val updateSecret = UpdateSecret("value".asPresent(), "description".asPresent())
                val response = client.patch("/organizations/$orgId/secrets/${secret.name}") {
                    setBody(updateSecret)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody secret.mapToApi()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path))?.value shouldBe "value"
            }
        }

        "handle a failure from the SecretStorage" {
            secretsTestApplication { client ->
                val secret = secretRepository.createOrganizationSecret(orgId, path = secretErrorPath)

                val updateSecret = UpdateSecret("value".asPresent(), "newDesc".asPresent())
                client.patch("/organizations/$orgId/secrets/${secret.name}") {
                    setBody(updateSecret)
                } shouldHaveStatus HttpStatusCode.InternalServerError

                secretRepository.getByIdAndName(OrganizationId(orgId), secret.name) shouldBe secret
            }
        }
    }
})
