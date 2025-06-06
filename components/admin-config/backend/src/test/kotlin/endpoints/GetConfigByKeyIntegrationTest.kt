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

package org.eclipse.apoapsis.ortserver.components.adminconfig.endpoints

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

import org.eclipse.apoapsis.ortserver.components.adminconfig.Config
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

class GetConfigByKeyIntegrationTest : AbstractIntegrationTest({
    "GetConfigByKey" should {
        "return the value and enabled status of an existing config key" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            insertOrUpdateConfig(dbExtension.db)
                            getConfigByKey(dbExtension.db)
                        }
                    }
                }

                // Insert a test config key to database
                client.post("/admin/config/HOME_ICON_URL") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        Config(
                            value = "https://example.com/existing_icon.png",
                            isEnabled = true,
                        )
                    )
                }

                val response = client.get("/admin/config/HOME_ICON_URL")

                response shouldHaveStatus HttpStatusCode.OK
                val config = response.body<Config>()
                config.value shouldBe "https://example.com/existing_icon.png"
                config.isEnabled shouldBe true
            }
        }

        "return the default value if the config key does not exist in db" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            getConfigByKey(dbExtension.db)
                        }
                    }
                }

                val response = client.get("/admin/config/HOME_ICON_URL")

                response shouldHaveStatus HttpStatusCode.OK
                val config = response.body<Config>()
                config.value shouldBe "https://example.com/icon.png"
                config.isEnabled shouldBe false
            }
        }

        "return BadRequest if the config key is invalid" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            getConfigByKey(dbExtension.db)
                        }
                    }
                }

                val response = client.get("/admin/config/INVALID_KEY")

                response shouldHaveStatus HttpStatusCode.BadRequest
            }
        }
    }
})
