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

package org.eclipse.apoapsis.ortserver.components.secrets.routes.repository

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.secrets.Secret
import org.eclipse.apoapsis.ortserver.components.secrets.SecretsIntegrationTest
import org.eclipse.apoapsis.ortserver.components.secrets.mapToApi
import org.eclipse.apoapsis.ortserver.components.secrets.routes.createOrganizationSecret
import org.eclipse.apoapsis.ortserver.components.secrets.routes.createProductSecret
import org.eclipse.apoapsis.ortserver.components.secrets.routes.createRepositorySecret

class GetAvailableRepositorySecretsIntegrationTest : SecretsIntegrationTest({
    var organizationId = 0L
    var productId = 0L
    var repoId = 0L

    beforeEach {
        organizationId = dbExtension.fixtures.organization.id
        productId = dbExtension.fixtures.product.id
        repoId = dbExtension.fixtures.repository.id
    }

    "GetAvailableRepositorySecrets" should {
        "return all secrets from the hierarchy" {
            secretsTestApplication { client ->
                val secret1 =
                    secretRepository.createOrganizationSecret(organizationId, "path1", "name1", "description1")
                val secret2 = secretRepository.createProductSecret(productId, "path2", "name2", "description2")
                val secret3 = secretRepository.createRepositorySecret(repoId, "path3", "name3", "description3")

                val response = client.get("/repositories/$repoId/availableSecrets")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<List<Secret>>() should containExactlyInAnyOrder(
                    secret1.mapToApi(),
                    secret2.mapToApi(),
                    secret3.mapToApi()
                )
            }
        }
    }
})
