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

class InsertOrUpdateConfigIntegrationTest : AbstractIntegrationTest({
    "InsertOrUpdateConfig" should {
        "insert the new configuration key if it doesn't exist" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            insertOrUpdateConfig(dbExtension.db)
                            getConfigByKey(dbExtension.db)
                        }
                    }
                }

                client.post("/admin/config/HOME_ICON_URL") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        Config(
                            value = "https://example.com/icon.png",
                            isEnabled = true,
                        )
                    )
                }

                val response = client.get("/admin/config/HOME_ICON_URL")

                response shouldHaveStatus HttpStatusCode.OK
                val config = response.body<Config>()
                config.value shouldBe "https://example.com/icon.png"
                config.isEnabled shouldBe true
            }
        }

        "update the value and isEnabled status of an existing configuration key" {
            integrationTestApplication { client ->
                application {
                    routing {
                        authenticate("test") {
                            insertOrUpdateConfig(dbExtension.db)
                            getConfigByKey(dbExtension.db)
                        }
                    }
                }

                client.post("/admin/config/HOME_ICON_URL") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        Config(
                            value = "https://example.com/icon.png",
                            isEnabled = true,
                        )
                    )
                }

                val response = client.get("/admin/config/HOME_ICON_URL")

                response shouldHaveStatus HttpStatusCode.OK
                val config = response.body<Config>()
                config.value shouldBe "https://example.com/icon.png"
                config.isEnabled shouldBe true

                client.post("/admin/config/HOME_ICON_URL") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        Config(
                            value = "https://changed/example.com/explicit_icon.png",
                            isEnabled = true,
                        )
                    )
                }

                val response2 = client.get("/admin/config/HOME_ICON_URL")

                response2 shouldHaveStatus HttpStatusCode.OK
                val config2 = response2.body<Config>()
                config2.value shouldBe "https://changed/example.com/explicit_icon.png"
                config2.isEnabled shouldBe true
            }
        }

        "return BadRequest if the configuration key is invalid" {
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
