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

package org.eclipse.apoapsis.ortserver.components.licensefindings.routes

import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import org.eclipse.apoapsis.ortserver.components.licensefindings.LicenseFinding
import org.eclipse.apoapsis.ortserver.components.licensefindings.LicenseFindingIntegrationTest
import org.eclipse.apoapsis.ortserver.components.licensefindings.SeedResult
import org.eclipse.apoapsis.ortserver.components.licensefindings.seedData
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty

class GetRunDetectedLicenseFindingsIntegrationTest : LicenseFindingIntegrationTest({
    lateinit var seeded: SeedResult

    beforeEach {
        seeded = seedData(dbExtension.fixtures, dbExtension.db)
    }

    "GetRunDetectedLicenseFindings" should {
        "return 200 with file-level findings sorted by path" {
            licenseFindingTestApplication { client ->
                val encodedIdentifier = URLEncoder.encode(
                    "${seeded.artifactIdentifier.type}:${seeded.artifactIdentifier.namespace}:" +
                            "${seeded.artifactIdentifier.name}:${seeded.artifactIdentifier.version}",
                    StandardCharsets.UTF_8
                )

                val response = client.get(
                    "/runs/${seeded.ortRunId}/detected-licenses/Apache-2.0/packages/$encodedIdentifier/findings"
                )

                response.status shouldBe HttpStatusCode.OK
                response.body<PagedResponse<LicenseFinding>>() shouldBe PagedResponse(
                    data = listOf(
                        LicenseFinding("docs/NOTICE.Apache", 11, 18, 87.5f, "LicenseScanner 1.0.0"),
                        LicenseFinding("LICENSE", 1, 10, 99f, "LicenseScanner 1.0.0")
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

        "return 200 with paginated findings when limit and offset are provided" {
            licenseFindingTestApplication { client ->
                val encodedIdentifier = URLEncoder.encode(
                    "${seeded.artifactIdentifier.type}:${seeded.artifactIdentifier.namespace}:" +
                            "${seeded.artifactIdentifier.name}:${seeded.artifactIdentifier.version}",
                    StandardCharsets.UTF_8
                )

                val response = client.get(
                    "/runs/${seeded.ortRunId}/detected-licenses/Apache-2.0/packages/" +
                            "$encodedIdentifier/findings?limit=1&offset=1"
                )

                response.status shouldBe HttpStatusCode.OK
                response.body<PagedResponse<LicenseFinding>>() shouldBe PagedResponse(
                    data = listOf(LicenseFinding("LICENSE", 1, 10, 99f, "LicenseScanner 1.0.0")),
                    pagination = PagingData(
                        limit = 1,
                        offset = 1,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("path", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "return 200 with an empty list for an unknown license" {
            licenseFindingTestApplication { client ->
                val encodedIdentifier = URLEncoder.encode(
                    "${seeded.artifactIdentifier.type}:${seeded.artifactIdentifier.namespace}:" +
                            "${seeded.artifactIdentifier.name}:${seeded.artifactIdentifier.version}",
                    StandardCharsets.UTF_8
                )

                val response = client.get(
                    "/runs/${seeded.ortRunId}/detected-licenses/Zlib/packages/$encodedIdentifier/findings"
                )

                response.status shouldBe HttpStatusCode.OK
                response.body<PagedResponse<LicenseFinding>>() shouldBe PagedResponse(
                    data = emptyList(),
                    pagination = PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 0,
                        sortProperties = listOf(SortProperty("path", SortDirection.ASCENDING))
                    )
                )
            }
        }
    }
})
