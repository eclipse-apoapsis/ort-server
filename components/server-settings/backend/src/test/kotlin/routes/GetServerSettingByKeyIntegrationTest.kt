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

package org.eclipse.apoapsis.ortserver.components.serversettings.routes

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.serversettings.ServerSetting
import org.eclipse.apoapsis.ortserver.components.serversettings.ServerSettingsIntegrationTest

class GetServerSettingByKeyIntegrationTest : ServerSettingsIntegrationTest({
    "GetServerSettingByKey" should {
        "return the value and enabled status of an existing server setting key" {
            serverSettingsTestApplication { client ->
                // Insert a test server setting into the database.
                client.post("/settings/server/HOME_ICON_URL") {
                    setBody(
                        ServerSetting(
                            value = "https://example.com/existing_icon.png",
                            isEnabled = true
                        )
                    )
                }

                val response = client.get("/settings/server/HOME_ICON_URL")
                response shouldHaveStatus HttpStatusCode.OK

                with(response.body<ServerSetting>()) {
                    value shouldBe "https://example.com/existing_icon.png"
                    isEnabled shouldBe true
                }
            }
        }

        "return the default value if the server setting key does not exist in db" {
            serverSettingsTestApplication { client ->
                val response = client.get("/settings/server/HOME_ICON_URL")
                response shouldHaveStatus HttpStatusCode.OK

                with(response.body<ServerSetting>()) {
                    value shouldBe "https://example.com/icon.png"
                    isEnabled shouldBe false
                }
            }
        }

        "return BadRequest if the server setting key is invalid" {
            serverSettingsTestApplication { client ->
                client.get("/settings/server/INVALID_KEY") shouldHaveStatus HttpStatusCode.BadRequest
            }
        }
    }
})
