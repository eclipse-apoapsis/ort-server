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
import org.eclipse.apoapsis.ortserver.components.snippetfindings.SnippetFinding
import org.eclipse.apoapsis.ortserver.components.snippetfindings.SnippetFindingIntegrationTest
import org.eclipse.apoapsis.ortserver.components.snippetfindings.seedData
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty

class GetRunProvenanceSnippetFindingsIntegrationTest : SnippetFindingIntegrationTest({
    lateinit var seeded: SeedResult

    beforeEach {
        seeded = seedData(dbExtension.fixtures, dbExtension.db)
    }

    "GetRunProvenanceSnippetFindings" should {
        "return 200 with paginated snippet findings sorted by path" {
            snippetFindingTestApplication { client ->
                val response = client.get(
                    "/runs/${seeded.ortRunId}/snippet-findings/provenances/${seeded.provenanceId}/findings" +
                            "?limit=1&offset=1"
                )

                response.status shouldBe HttpStatusCode.OK
                response.body<PagedResponse<SnippetFinding>>() shouldBe PagedResponse(
                    data = listOf(
                        SnippetFinding(seeded.secondFindingId, "src/test/AppTest.kt", 3, 7, 1)
                    ),
                    pagination = PagingData(
                        limit = 1,
                        offset = 1,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("path", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "return 200 with correct snippet counts per finding" {
            snippetFindingTestApplication { client ->
                val response = client.get(
                    "/runs/${seeded.ortRunId}/snippet-findings/provenances/${seeded.provenanceId}/findings"
                )

                response.status shouldBe HttpStatusCode.OK
                response.body<PagedResponse<SnippetFinding>>() shouldBe PagedResponse(
                    data = listOf(
                        SnippetFinding(seeded.firstFindingId, "src/main/App.kt", 12, 18, 2),
                        SnippetFinding(seeded.secondFindingId, "src/test/AppTest.kt", 3, 7, 1)
                    ),
                    pagination = PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("path", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "return 404 when run does not exist" {
            snippetFindingTestApplication { client ->
                val response = client.get(
                    "/runs/999999/snippet-findings/provenances/${seeded.provenanceId}/findings"
                )

                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        "return 404 when provenance does not belong to the given run" {
            snippetFindingTestApplication { client ->
                val response = client.get(
                    "/runs/${seeded.ortRunId}/snippet-findings/provenances/${seeded.otherProvenanceId}/findings"
                )

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})
