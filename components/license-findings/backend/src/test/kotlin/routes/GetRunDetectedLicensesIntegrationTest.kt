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

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.licensefindings.DetectedLicense
import org.eclipse.apoapsis.ortserver.components.licensefindings.LicenseFindingIntegrationTest
import org.eclipse.apoapsis.ortserver.components.licensefindings.seedData
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty

class GetRunDetectedLicensesIntegrationTest : LicenseFindingIntegrationTest({
    var ortRunId = -1L

    beforeEach {
        ortRunId = seedData(dbExtension.fixtures, dbExtension.db).ortRunId
    }

    "GetRunDetectedLicenses" should {
        "return 200 with all licenses and package counts sorted by license" {
            licenseFindingTestApplication { client ->
                val response = client.get("/runs/$ortRunId/detected-licenses")

                response.status shouldBe HttpStatusCode.OK
                response.body<PagedResponse<DetectedLicense>>() shouldBe PagedResponse(
                    data = listOf(
                        DetectedLicense("Apache-2.0", 2),
                        DetectedLicense("BSD-3-Clause", 1),
                        DetectedLicense("MIT", 1)
                    ),
                    pagination = PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 3,
                        sortProperties = listOf(SortProperty("license", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "filter by the license query parameter case-insensitively" {
            licenseFindingTestApplication { client ->
                val response = client.get("/runs/$ortRunId/detected-licenses?license=apache")

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<PagedResponse<DetectedLicense>>()
                body.pagination.totalCount shouldBe 1
                body.data shouldContainExactly listOf(DetectedLicense("Apache-2.0", 2))
            }
        }

        "return 404 for an unknown run" {
            licenseFindingTestApplication { client ->
                val response = client.get("/runs/999999/detected-licenses")

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})
