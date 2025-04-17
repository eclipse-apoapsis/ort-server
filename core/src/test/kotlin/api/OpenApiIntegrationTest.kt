/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

import org.eclipse.apoapsis.ortserver.core.createJsonClient
import org.eclipse.apoapsis.ortserver.core.plugins.configureOpenApi
import org.eclipse.apoapsis.ortserver.core.testutils.TestConfig
import org.eclipse.apoapsis.ortserver.core.testutils.ortServerTestApplication
import org.eclipse.apoapsis.ortserver.utils.system.ORT_SERVER_VERSION
import org.eclipse.apoapsis.ortserver.utils.test.Integration

class OpenApiIntegrationTest : WordSpec({
    tags(Integration)

    "/swagger-ui/api.json" should {
        "return the API specification" {
            ortServerTestApplication(
                config = TestConfig.Test,
                additionalConfigs = mapOf("jwt.issuer" to "https://example.org")
            ) {
                application { configureOpenApi() }

                val response = createJsonClient().get("/swagger-ui/api.json")

                response shouldHaveStatus 200

                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

                json["openapi"].shouldBeInstanceOf<JsonPrimitive>().content shouldBe "3.1.0"

                val info = json["info"].shouldNotBeNull().jsonObject
                info["title"].shouldBeInstanceOf<JsonPrimitive>().content shouldBe "ORT Server API"
                info["version"].shouldBeInstanceOf<JsonPrimitive>().content shouldBe ORT_SERVER_VERSION

                val license = info["license"].shouldNotBeNull().jsonObject
                license["name"].shouldBeInstanceOf<JsonPrimitive>().content shouldBe "Apache-2.0"
                license["url"].shouldBeInstanceOf<JsonPrimitive>().content shouldBe
                        "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }
    }
})
