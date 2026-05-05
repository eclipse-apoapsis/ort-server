/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.snippetfindings.routes

import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.snippetfindings.SeedResult
import org.eclipse.apoapsis.ortserver.components.snippetfindings.SnippetFindingIntegrationTest
import org.eclipse.apoapsis.ortserver.components.snippetfindings.SnippetSource
import org.eclipse.apoapsis.ortserver.components.snippetfindings.seedData
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetFindingDao
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty

import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class GetRunSnippetFindingSnippetsIntegrationTest : SnippetFindingIntegrationTest({
    lateinit var seeded: SeedResult
    var emptyFindingId = -1L

    beforeEach {
        seeded = seedData(dbExtension.fixtures, dbExtension.db)

        transaction(dbExtension.db) {
            val existingFinding = SnippetFindingDao[seeded.firstFindingId]

            emptyFindingId = SnippetFindingDao.new {
                path = "src/empty/NoSource.kt"
                startLine = 1
                endLine = 2
                scanSummary = existingFinding.scanSummary
            }.id.value
        }
    }

    "GetRunSnippetFindingSnippets" should {
        "return 200 with paginated upstream snippets including artifact and VCS provenance" {
            snippetFindingTestApplication { client ->
                val response = client.get(
                    "/runs/${seeded.ortRunId}/snippet-findings/${seeded.firstFindingId}/snippets?limit=1&offset=1"
                )

                response.status shouldBe HttpStatusCode.OK
                response.body<PagedResponse<SnippetSource>>() shouldBe PagedResponse(
                    data = listOf(
                        SnippetSource(
                            purl = "pkg:maven/com.example/upstream-vcs@2.0",
                            path = "lib/Utils.kt",
                            startLine = 10,
                            endLine = 14,
                            license = "MIT",
                            score = 88.5f,
                            vcsType = "GIT",
                            vcsUrl = "https://example.com/scm/upstream-vcs.git",
                            vcsRevision = "cafebabe",
                            vcsPath = "modules/core"
                        )
                    ),
                    pagination = PagingData(
                        limit = 1,
                        offset = 1,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("purl", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "return 200 with empty list for a snippet finding that has no snippets" {
            snippetFindingTestApplication { client ->
                val response = client.get("/runs/${seeded.ortRunId}/snippet-findings/$emptyFindingId/snippets")

                response.status shouldBe HttpStatusCode.OK
                response.body<PagedResponse<SnippetSource>>() shouldBe PagedResponse(
                    data = emptyList(),
                    pagination = PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 0,
                        sortProperties = listOf(SortProperty("purl", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "return 404 when snippet finding does not belong to the given run" {
            snippetFindingTestApplication { client ->
                val response = client.get(
                    "/runs/${seeded.ortRunId}/snippet-findings/${seeded.otherFindingId}/snippets"
                )

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})
