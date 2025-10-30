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

package org.eclipse.apoapsis.ortserver.components.resolutions

import com.auth0.jwt.JWT

import io.ktor.client.HttpClient
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.authentication
import io.ktor.server.auth.principal
import io.ktor.server.testing.ApplicationTestBuilder

import io.mockk.mockk

import java.util.Base64

import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.OrtPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.roles.Superuser
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

import org.jetbrains.exposed.sql.Database

@Suppress("UnnecessaryAbstractClass")
abstract class ResolutionsIntegrationTest(body: ResolutionsIntegrationTest.() -> Unit) : AbstractIntegrationTest({}) {
    lateinit var ortRunService: OrtRunService
    lateinit var vulnerabilityResolutionDefinitionService: VulnerabilityResolutionDefinitionService

    private lateinit var db: Database
    private lateinit var fixtures: Fixtures

    init {
        beforeEach {
            db = dbExtension.db
            fixtures = dbExtension.fixtures

            ortRunService = OrtRunService(
                db,
                fixtures.advisorJobRepository,
                fixtures.advisorRunRepository,
                fixtures.analyzerJobRepository,
                fixtures.analyzerRunRepository,
                fixtures.evaluatorJobRepository,
                fixtures.evaluatorRunRepository,
                fixtures.ortRunRepository,
                fixtures.reporterJobRepository,
                fixtures.reporterRunRepository,
                fixtures.notifierJobRepository,
                fixtures.notifierRunRepository,
                fixtures.repositoryConfigurationRepository,
                fixtures.repositoryRepository,
                fixtures.resolvedConfigurationRepository,
                fixtures.scannerJobRepository,
                fixtures.scannerRunRepository,
                mockk(),
                mockk()
            )

            vulnerabilityResolutionDefinitionService = VulnerabilityResolutionDefinitionService(db, ortRunService)
        }

        body()
    }

    fun resolutionsTestApplication(
        block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit
    ) = integrationTestApplication(
        routes = {
            // Define a route-scoped plugin that injects a principal for tests
            val injectTestPrincipal = createRouteScopedPlugin(name = "InjectTestPrincipal") {
                onCall { call ->
                    if (call.principal<OrtPrincipal>() == null) {
                        val headerJson = """{"alg":"none","typ":"JWT"}"""
                        val payloadJson = """
                            {
                              "sub": "user-1",
                              "preferred_username": "test",
                              "name": "Test User"
                            }
                        """.trimIndent()

                        fun b64url(s: String) =
                            Base64.getUrlEncoder().withoutPadding()
                                .encodeToString(s.toByteArray(Charsets.UTF_8))

                        val token = "${b64url(headerJson)}.${b64url(payloadJson)}."
                        val decoded = JWT.decode(token)

                        val principal = OrtPrincipal(
                            payload = decoded,
                            roles = setOf(Superuser.ROLE_NAME)
                        )

                        call.authentication.principal(principal)
                    }
                }
            }

            install(injectTestPrincipal)

            resolutionsRoutes(ortRunService, vulnerabilityResolutionDefinitionService)
        },
        block = block
    )
}
