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

package org.eclipse.apoapsis.ortserver.components.secrets.routes.repository

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.secrets.SecretsIntegrationTest
import org.eclipse.apoapsis.ortserver.components.secrets.routes.createRepositorySecret
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting

class DeleteSecretByRepositoryIdAndNameIntegrationTest : SecretsIntegrationTest({
    var repoId = 0L

    beforeEach {
        repoId = dbExtension.fixtures.repository.id
    }

    "DeleteSecretByRepositoryIdAndName" should {
        "delete a secret" {
            secretsTestApplication { client ->
                val secret = secretRepository.createRepositorySecret(repoId)

                client.delete("/repositories/$repoId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.NoContent

                secretRepository.listForId(RepositoryId(repoId)).data shouldBe emptyList()

                val provider = SecretsProviderFactoryForTesting.instance()
                provider.readSecret(Path(secret.path)) should beNull()
            }
        }

        "handle a failure from the SecretStorage" {
            secretsTestApplication { client ->
                val secret = secretRepository.createRepositorySecret(repoId, path = secretErrorPath)

                client.delete("/repositories/$repoId/secrets/${secret.name}") shouldHaveStatus
                        HttpStatusCode.InternalServerError

                secretRepository.getByIdAndName(RepositoryId(repoId), secret.name) shouldBe secret
            }
        }
    }
})
