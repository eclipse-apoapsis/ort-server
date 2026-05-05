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

import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier
import org.eclipse.apoapsis.ortserver.components.snippetfindings.SeedResult
import org.eclipse.apoapsis.ortserver.components.snippetfindings.SnippetFindingIntegrationTest
import org.eclipse.apoapsis.ortserver.components.snippetfindings.SnippetFindingProvenance
import org.eclipse.apoapsis.ortserver.components.snippetfindings.seedData
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty

class GetRunSnippetFindingProvenancesIntegrationTest : SnippetFindingIntegrationTest({
    lateinit var seeded: SeedResult

    beforeEach {
        seeded = seedData(dbExtension.fixtures, dbExtension.db)
    }

    "GetRunSnippetFindingProvenances" should {
        "return 200 with the scan result provenance for the run" {
            snippetFindingTestApplication { client ->
                val response = client.get("/runs/${seeded.ortRunId}/snippet-findings/provenances")

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<PagedResponse<SnippetFindingProvenance>>()
                body.pagination shouldBe PagingData(
                    limit = DEFAULT_LIMIT,
                    offset = 0,
                    totalCount = 1,
                    sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING))
                )
                body.data.size shouldBe 1
                body.data[0].id shouldBe seeded.provenanceId
                body.data[0].identifier shouldBe Identifier("Maven", "com.example", "artifact-package", "1.0")
                body.data[0].provenanceType shouldBe "REPOSITORY"
                body.data[0].vcsUrl shouldBe "https://example.com/scm/artifact-package.git"
                body.data[0].vcsRevision shouldBe "abcdef1234567890"
            }
        }

        "not include provenances from other runs" {
            snippetFindingTestApplication { client ->
                val response = client.get("/runs/${seeded.ortRunId}/snippet-findings/provenances")

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<PagedResponse<SnippetFindingProvenance>>()
                body.data.none { it.id == seeded.otherProvenanceId } shouldBe true
            }
        }

        "return 404 when run does not exist" {
            snippetFindingTestApplication { client ->
                val response = client.get("/runs/999999/snippet-findings/provenances")

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})
