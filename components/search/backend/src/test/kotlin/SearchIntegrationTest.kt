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

package ort.eclipse.apoapsis.ortserver.components.search

import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.serialization
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

import io.mockk.coEvery
import io.mockk.mockk

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.components.search.backend.SearchService
import org.eclipse.apoapsis.ortserver.components.search.searchRoutes
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest
import org.eclipse.apoapsis.ortserver.shared.ktorutils.DummyConfig
import org.eclipse.apoapsis.ortserver.shared.ktorutils.FakeAuthenticationProvider
import org.eclipse.apoapsis.ortserver.shared.ktorutils.createJsonClient
import org.eclipse.apoapsis.ortserver.shared.ktorutils.respondError

import org.jetbrains.exposed.sql.Database

/** An [AbstractIntegrationTest] pre-configured for testing the search routes. */
@Suppress("UnnecessaryAbstractClass")
abstract class SearchIntegrationTest(
    body: SearchIntegrationTest.() -> Unit
) : AbstractIntegrationTest({}) {
    lateinit var searchService: SearchService
    lateinit var hierarchyAuthorizationService: AuthorizationService

    private lateinit var db: Database
    private lateinit var fixtures: Fixtures

    init {
        beforeEach {
            db = dbExtension.db
            fixtures = dbExtension.fixtures
            hierarchyAuthorizationService = mockk {
                coEvery {
                    filterHierarchyIds(any(), any(), any(), any(), any())
                } returns HierarchyFilter.WILDCARD
            }
            searchService = SearchService(db, hierarchyAuthorizationService)
        }

        body()
    }

    fun searchTestApplication(
        block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit
    ) {
        testApplication {
            application {
                install(ContentNegotiation) {
                    serialization(ContentType.Application.Json, Json)
                }

                install(Authentication) {
                    register(FakeAuthenticationProvider(DummyConfig(principal)))
                }

                install(StatusPages) {
                    exception<QueryParametersException> { call, e ->
                        call.respondError(HttpStatusCode.BadRequest, "Invalid query parameters.", e.message)
                    }
                }

                routing {
                    authenticate("test") {
                        searchRoutes(searchService)
                    }
                }
            }

            block(createJsonClient())
        }
    }
}
