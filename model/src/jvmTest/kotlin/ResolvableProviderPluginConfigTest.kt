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

package org.eclipse.apoapsis.ortserver.model

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import kotlinx.serialization.json.Json

class ResolvableProviderPluginConfigTest : WordSpec({
    "deserialization" should {
        "work for up-to-date input" {
            val input = """
            {
              "type": "example-provider",
              "id": "example-provider",
              "enabled": true,
              "options": {
                "option1": "value1",
                "option2": "value2"
              },
              "secrets": {
                "secret1": {
                  "name": "admin-secret",
                  "source": "ADMIN"
                },
                "secret2": {
                  "name": "user-secret",
                  "source": "USER"
                }
              }
            }
            """.trimIndent()

            Json.decodeFromString<ResolvableProviderPluginConfig>(input) shouldBe ResolvableProviderPluginConfig(
                type = "example-provider",
                id = "example-provider",
                enabled = true,
                options = mapOf(
                    "option1" to "value1",
                    "option2" to "value2"
                ),
                secrets = mapOf(
                    "secret1" to ResolvableSecret(
                        name = "admin-secret",
                        source = SecretSource.ADMIN
                    ),
                    "secret2" to ResolvableSecret(
                        name = "user-secret",
                        source = SecretSource.USER
                    )
                )
            )
        }

        "work for legacy input" {
            val input = """
            {
              "type": "example-provider",
              "id": "example-provider",
              "enabled": true,
              "options": {
                "option1": "value1",
                "option2": "value2"
              },
              "secrets": {
                "secret1": "admin-secret",
                "secret2": "user-secret"
              }
            }
            """.trimIndent()

            Json.decodeFromString<ResolvableProviderPluginConfig>(input) shouldBe ResolvableProviderPluginConfig(
                type = "example-provider",
                id = "example-provider",
                enabled = true,
                options = mapOf(
                    "option1" to "value1",
                    "option2" to "value2"
                ),
                secrets = mapOf(
                    "secret1" to ResolvableSecret(
                        name = "admin-secret",
                        source = SecretSource.ADMIN
                    ),
                    "secret2" to ResolvableSecret(
                        name = "user-secret",
                        source = SecretSource.ADMIN
                    )
                )
            )
        }
    }
})
