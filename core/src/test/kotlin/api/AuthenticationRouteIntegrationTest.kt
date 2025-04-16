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

package org.eclipse.apoapsis.ortserver.core.api

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.api.v1.model.OidcConfig
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.TEST_SUBJECT_CLIENT
import org.eclipse.apoapsis.ortserver.utils.test.Integration

class AuthenticationRouteIntegrationTest : AbstractIntegrationTest({
    tags(Integration)

    "GET /auth/oidc-config/cli" should {
        "return the default OIDC config" {
            integrationTestApplication {
                val response = unauthenticatedClient.get("api/v1/auth/oidc-config/cli")

                response shouldHaveStatus HttpStatusCode.OK
                val body = response.body<OidcConfig>()

                with(body) {
                    accessTokenUrl shouldEndWith "/protocol/openid-connect/token"
                    clientId shouldBe TEST_SUBJECT_CLIENT
                }
            }
        }
    }
})
