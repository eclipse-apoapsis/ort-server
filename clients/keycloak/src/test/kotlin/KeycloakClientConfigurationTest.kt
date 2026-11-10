/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.clients.keycloak

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.config.ConfigManager

class KeycloakClientConfigurationTest : WordSpec({
    "create()" should {
        "create a valid Keycloak client configuration" {
            val config = mapOf(
                "keycloak.baseUrl" to "http://localhost:8080",
                "keycloak.realm" to "myrealm",
                "keycloak.clientId" to "myclient",
                "keycloak.apiUser" to "user",
                "keycloak.apiSecret" to "secret"
            )

            val configManager = ConfigManager.create(ConfigFactory.parseMap(config))
            val keycloakClientConfig = KeycloakClientConfiguration.create(configManager)

            keycloakClientConfig.baseUrl shouldBe "http://localhost:8080"
            keycloakClientConfig.realm shouldBe "myrealm"
            keycloakClientConfig.clientId shouldBe "myclient"
            keycloakClientConfig.apiSecret shouldBe "secret"
            keycloakClientConfig.apiUser shouldBe "user"
            keycloakClientConfig.apiUrl shouldBe "http://localhost:8080/admin/realms/myrealm"
            keycloakClientConfig.accessTokenUrl shouldBe
                    "http://localhost:8080/realms/myrealm/protocol/openid-connect/token"
        }
    }
})
