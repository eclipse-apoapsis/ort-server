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

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

import org.eclipse.apoapsis.ortserver.components.adminconfig.Config
import org.eclipse.apoapsis.ortserver.components.adminconfig.ConfigKey
import org.eclipse.apoapsis.ortserver.components.adminconfig.adminConfigRoutes
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

class AdminConfigAuthorizationTest : AbstractAuthorizationTest({
    "GetConfigByKey" should {
        "require authentication" {
            val configKey = ConfigKey.HOME_ICON_URL

            requestShouldRequireAuthentication(routes = { adminConfigRoutes(dbExtension.db) }) {
                get("/admin/config/$configKey")
            }
        }
    }

    "InsertOrUpdateConfig" should {
        "require the superuser role" {
            val configKey = ConfigKey.HOME_ICON_URL
            val body = Config(value = "https://example.com/icon.png", isEnabled = true)

            requestShouldRequireSuperuser(routes = { adminConfigRoutes(dbExtension.db) }) {
                post("/admin/config/$configKey") {
                    setBody(body)
                }
            }
        }
    }
})
