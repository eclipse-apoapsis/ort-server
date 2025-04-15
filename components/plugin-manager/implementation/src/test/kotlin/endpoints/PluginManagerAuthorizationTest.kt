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
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.WordSpec

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.serialization
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.clients.keycloak.DefaultKeycloakClient.Companion.configureAuthentication
import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupName
import org.eclipse.apoapsis.ortserver.clients.keycloak.RoleName
import org.eclipse.apoapsis.ortserver.clients.keycloak.User
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserId
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.KeycloakTestExtension
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.TEST_SUBJECT_CLIENT
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.addUserRole
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.createJwtConfigMapForTestRealm
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.createKeycloakClientConfigurationForTestRealm
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.createKeycloakClientForTestRealm
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.createKeycloakConfigMapForTestRealm
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.setUpClientScope
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.setUpUser
import org.eclipse.apoapsis.ortserver.components.authorization.AuthorizationException
import org.eclipse.apoapsis.ortserver.components.authorization.SecurityConfigurations
import org.eclipse.apoapsis.ortserver.components.authorization.configureAuthentication
import org.eclipse.apoapsis.ortserver.components.authorization.roles.Superuser
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.utils.test.Integration

import org.ossreviewtoolkit.plugins.advisors.vulnerablecode.VulnerableCodeFactory

private val TEST_USER = User(
    id = UserId("test-user-id"),
    username = UserName("test-user"),
    firstName = "Test",
    lastName = "User",
    email = "test-user@example.org"
)

private const val TEST_USER_PASSWORD = "password"

class PluginManagerAuthorizationTest : WordSpec({
    tags(Integration)

    val dbExtension = extension(DatabaseTestExtension())

    val keycloak = install(KeycloakTestExtension(createRealmPerTest = true)) {
        setUpUser(TEST_USER, TEST_USER_PASSWORD)
        setUpClientScope(TEST_SUBJECT_CLIENT)
    }

    val json = Json { ignoreUnknownKeys = true }

    val keycloakClient = keycloak.createKeycloakClientForTestRealm()
    val keycloakConfig = keycloak.createKeycloakConfigMapForTestRealm()
    val jwtConfig = keycloak.createJwtConfigMapForTestRealm()

    beforeEach {
        val roleName = RoleName(Superuser.ROLE_NAME)
        val groupName = GroupName(Superuser.GROUP_NAME)
        keycloakClient.createRole(name = roleName)
        keycloakClient.createGroup(groupName)
        val group = keycloakClient.getGroup(groupName)
        val role = keycloakClient.getRole(roleName)
        keycloakClient.addGroupClientRole(group.id, role)
    }

    "DisablePlugin" should {
        "require the superuser role" {
            testApplication {
                val config = MapApplicationConfig()
                (keycloakConfig + jwtConfig).forEach { config.put(it.key, it.value) }
                config.put("jwt.audience", TEST_SUBJECT_CLIENT)

                environment {
                    this.config = config
                }

                application {
                    install(ContentNegotiation) {
                        serialization(ContentType.Application.Json, json)
                    }

                    install(StatusPages) {
                        exception<AuthorizationException> { call, _ ->
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }

                    configureAuthentication(config, keycloakClient)

                    routing {
                        authenticate(SecurityConfigurations.TOKEN) {
                            disablePlugin(PluginEventStore(dbExtension.db))
                        }
                    }
                }

                val pluginType = PluginType.ADVISOR
                val pluginId = VulnerableCodeFactory.descriptor.id

                val clientConfig = keycloak.createKeycloakClientConfigurationForTestRealm(
                    user = TEST_USER.username.value,
                    secret = TEST_USER_PASSWORD
                )
                val client = createJsonClient().configureAuthentication(clientConfig, json)

                client.post("/admin/plugins/$pluginType/$pluginId/disable") shouldHaveStatus HttpStatusCode.Forbidden
                keycloak.keycloakAdminClient.addUserRole(TEST_USER.username.value, Superuser.ROLE_NAME)
                client.post("/admin/plugins/$pluginType/$pluginId/disable") shouldHaveStatus HttpStatusCode.Accepted
            }
        }
    }

    "EnablePlugin" should {
        "require the superuser role" {
            testApplication {
                val config = MapApplicationConfig()
                (keycloakConfig + jwtConfig).forEach { config.put(it.key, it.value) }
                config.put("jwt.audience", TEST_SUBJECT_CLIENT)

                environment {
                    this.config = config
                }

                application {
                    install(ContentNegotiation) {
                        serialization(ContentType.Application.Json, json)
                    }

                    install(StatusPages) {
                        exception<AuthorizationException> { call, _ ->
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }

                    configureAuthentication(config, keycloakClient)

                    routing {
                        authenticate(SecurityConfigurations.TOKEN) {
                            enablePlugin(PluginEventStore(dbExtension.db))
                        }
                    }
                }

                val pluginType = PluginType.ADVISOR
                val pluginId = VulnerableCodeFactory.descriptor.id

                val clientConfig = keycloak.createKeycloakClientConfigurationForTestRealm(
                    user = TEST_USER.username.value,
                    secret = TEST_USER_PASSWORD
                )
                val client = createJsonClient().configureAuthentication(clientConfig, json)

                client.post("/admin/plugins/$pluginType/$pluginId/enable") shouldHaveStatus HttpStatusCode.Forbidden
                keycloak.keycloakAdminClient.addUserRole(TEST_USER.username.value, Superuser.ROLE_NAME)
                client.post("/admin/plugins/$pluginType/$pluginId/enable") shouldHaveStatus HttpStatusCode.NotModified
            }
        }
    }

    "GetInstalledPlugins" should {
        "require the superuser role" {
            testApplication {
                val config = MapApplicationConfig()
                (keycloakConfig + jwtConfig).forEach { config.put(it.key, it.value) }
                config.put("jwt.audience", TEST_SUBJECT_CLIENT)

                environment {
                    this.config = config
                }

                application {
                    install(ContentNegotiation) {
                        serialization(ContentType.Application.Json, json)
                    }

                    install(StatusPages) {
                        exception<AuthorizationException> { call, _ ->
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }

                    configureAuthentication(config, keycloakClient)

                    routing {
                        authenticate(SecurityConfigurations.TOKEN) {
                            getInstalledPlugins(dbExtension.db)
                        }
                    }
                }

                val clientConfig = keycloak.createKeycloakClientConfigurationForTestRealm(
                    user = TEST_USER.username.value,
                    secret = TEST_USER_PASSWORD
                )
                val client = createJsonClient().configureAuthentication(clientConfig, json)

                client.get("/admin/plugins") shouldHaveStatus HttpStatusCode.Forbidden
                keycloak.keycloakAdminClient.addUserRole(TEST_USER.username.value, Superuser.ROLE_NAME)
                client.get("/admin/plugins") shouldHaveStatus HttpStatusCode.OK
            }
        }
    }
})
