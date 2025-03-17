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

package org.eclipse.apoapsis.ortserver.components.pluginmanager.endpoints

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.shouldNot

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.serialization
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginDescriptor
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.utils.test.Integration

class GetInstalledPluginsIntegrationTest : WordSpec({
    tags(Integration)

    "GetInstalledPlugins" should {
        "return all installed ORT plugins" {
            testApplication {
                application {
                    install(ContentNegotiation) {
                        serialization(ContentType.Application.Json, Json)
                    }

                    routing {
                        getInstalledPlugins()
                    }
                }

                val client = createJsonClient()
                val response = client.get("/admin/plugins")

                response shouldHaveStatus HttpStatusCode.OK
                val pluginDescriptors = response.body<List<PluginDescriptor>>()
                enumValues<PluginType>().forEach { pluginType ->
                    pluginDescriptors.filter { it.type == pluginType } shouldNot beEmpty()
                }
            }
        }
    }
})

fun ApplicationTestBuilder.createJsonClient() = createClient {
    install(ClientContentNegotiation) {
        json(Json)
    }
}
