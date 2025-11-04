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
import io.ktor.server.testing.ApplicationTestBuilder

import org.eclipse.apoapsis.ortserver.components.search.backend.SearchService
import org.eclipse.apoapsis.ortserver.components.search.searchRoutes
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

import org.jetbrains.exposed.sql.Database

/** An [AbstractIntegrationTest] pre-configured for testing the search routes. */
@Suppress("UnnecessaryAbstractClass")
abstract class SearchIntegrationTest(
    body: SearchIntegrationTest.() -> Unit
) : AbstractIntegrationTest({}) {
    lateinit var searchService: SearchService

    private lateinit var db: Database
    private lateinit var fixtures: Fixtures

    init {
        beforeEach {
            db = dbExtension.db
            fixtures = dbExtension.fixtures
            searchService = SearchService(db)
        }

        body()
    }

    fun searchTestApplication(
        block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit
    ) = integrationTestApplication(
        routes = { searchRoutes(searchService) },
        block = block
    )
}
