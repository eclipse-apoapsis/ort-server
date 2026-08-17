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

import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.licensefindings.DetectedLicense
import org.eclipse.apoapsis.ortserver.components.licensefindings.LicenseFindingIntegrationTest
import org.eclipse.apoapsis.ortserver.components.licensefindings.SeedResult
import org.eclipse.apoapsis.ortserver.components.licensefindings.addLicenseFindings
import org.eclipse.apoapsis.ortserver.components.licensefindings.seedData
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty

class GetRunDetectedLicensesIntegrationTest : LicenseFindingIntegrationTest({
    lateinit var seeded: SeedResult

    beforeEach {
        seeded = seedData(dbExtension.fixtures, dbExtension.db)
    }

    "GetRunDetectedLicenses" should {
        "return 200 with all licenses and package counts sorted by license" {
            licenseFindingTestApplication { client ->
                val response = client.get("/runs/${seeded.ortRunId}/detected-licenses")

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

        "filter by multiple exact license expressions" {
            licenseFindingTestApplication { client ->
                val response = client.get("/runs/${seeded.ortRunId}/detected-licenses") {
                    parameter("license", "MIT,Apache-2.0")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<PagedResponse<DetectedLicense>>()
                body.pagination.totalCount shouldBe 2
                body.data should containExactly(
                    DetectedLicense("Apache-2.0", 2),
                    DetectedLicense("MIT", 1)
                )
            }
        }

        "not match a longer expression when filtering by one exact license" {
            addLicenseFindings(
                dbExtension.db,
                seeded.scannerRunId,
                Identifier("Maven", "com.example", "mit-or-apache", "1.0"),
                listOf("MIT OR Apache-2.0")
            )

            licenseFindingTestApplication { client ->
                val response = client.get("/runs/${seeded.ortRunId}/detected-licenses") {
                    parameter("license", "MIT")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<PagedResponse<DetectedLicense>>()
                body.pagination.totalCount shouldBe 1
                body.data should containExactly(DetectedLicense("MIT", 1))
            }
        }

        "search by a case-insensitive license substring" {
            addLicenseFindings(
                dbExtension.db,
                seeded.scannerRunId,
                Identifier("Maven", "com.example", "mit-or-apache", "1.0"),
                listOf("MIT OR Apache-2.0")
            )

            licenseFindingTestApplication { client ->
                val response = client.get("/runs/${seeded.ortRunId}/detected-licenses") {
                    parameter("licenseSearch", "mit")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<PagedResponse<DetectedLicense>>()
                body.pagination.totalCount shouldBe 2
                body.data should containExactly(
                    DetectedLicense("MIT", 1),
                    DetectedLicense("MIT OR Apache-2.0", 1)
                )
            }
        }

        "exclude exact license expressions" {
            licenseFindingTestApplication { client ->
                val response = client.get("/runs/${seeded.ortRunId}/detected-licenses") {
                    parameter("license", "-,MIT")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<PagedResponse<DetectedLicense>>()
                body.pagination.totalCount shouldBe 2
                body.data should containExactly(
                    DetectedLicense("Apache-2.0", 2),
                    DetectedLicense("BSD-3-Clause", 1)
                )
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
