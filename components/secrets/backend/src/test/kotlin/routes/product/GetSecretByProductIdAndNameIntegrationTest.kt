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

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.secrets.SecretsIntegrationTest
import org.eclipse.apoapsis.ortserver.components.secrets.mapToApi
import org.eclipse.apoapsis.ortserver.components.secrets.routes.createProductSecret
import org.eclipse.apoapsis.ortserver.shared.ktorutils.shouldHaveBody

class GetSecretByProductIdAndNameIntegrationTest : SecretsIntegrationTest({
    var prodId = 0L

    beforeEach {
        prodId = dbExtension.fixtures.product.id
    }

    "GetSecretByProductIdAndName" should {
        "return a single secret" {
            secretsTestApplication { client ->
                val secret = secretRepository.createProductSecret(prodId)

                val response = client.get("/products/$prodId/secrets/${secret.name}")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody secret.mapToApi()
            }
        }

        "respond with NotFound if no secret exists" {
            secretsTestApplication { client ->
                client.get("/products/$prodId/secrets/999999") shouldHaveStatus
                        HttpStatusCode.NotFound
            }
        }
    }
})
