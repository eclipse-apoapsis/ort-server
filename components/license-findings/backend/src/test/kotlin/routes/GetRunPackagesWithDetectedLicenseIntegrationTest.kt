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

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.components.licensefindings.LicenseFindingIntegrationTest
import org.eclipse.apoapsis.ortserver.components.licensefindings.PackageIdentifier
import org.eclipse.apoapsis.ortserver.components.licensefindings.SeedResult
import org.eclipse.apoapsis.ortserver.components.licensefindings.seedData
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty

class GetRunPackagesWithDetectedLicenseIntegrationTest : LicenseFindingIntegrationTest({
    lateinit var seeded: SeedResult

    beforeEach {
        seeded = seedData(dbExtension.fixtures, dbExtension.db)
    }

    "GetRunPackagesWithDetectedLicense" should {
        "return 200 with the package containing the given license" {
            licenseFindingTestApplication { client ->
                val response = client.get(
                    "/runs/${seeded.ortRunId}/detected-licenses/MIT/packages"
                )

                response.status shouldBe HttpStatusCode.OK
                response.body<PagedResponse<PackageIdentifier>>() shouldBe PagedResponse(
                    data = listOf(
                        PackageIdentifier(
                            seeded.vcsIdentifier.mapToApi(),
                            seeded.vcsPurl
                        )
                    ),
                    pagination = PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 1,
                        sortProperties = listOf(SortProperty("identifier", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "filter by the identifier query parameter" {
            licenseFindingTestApplication { client ->
                val response = client.get(
                    "/runs/${seeded.ortRunId}/detected-licenses/Apache-2.0/packages?identifier=vcs"
                )

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<PagedResponse<PackageIdentifier>>()
                body.pagination.totalCount shouldBe 1
                body.data shouldContainExactly listOf(
                    PackageIdentifier(
                        seeded.vcsIdentifier.mapToApi(),
                        seeded.vcsPurl
                    )
                )
            }
        }

        "filter by the purl query parameter" {
            licenseFindingTestApplication { client ->
                val response = client.get(
                    "/runs/${seeded.ortRunId}/detected-licenses/Apache-2.0/packages?purl=artifact-package%401.0"
                )

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<PagedResponse<PackageIdentifier>>()
                body.pagination.totalCount shouldBe 1
                body.data shouldContainExactly listOf(
                    PackageIdentifier(
                        seeded.artifactIdentifier.mapToApi(),
                        seeded.artifactPurl
                    )
                )
            }
        }

        "sort by purl" {
            licenseFindingTestApplication { client ->
                val response = client.get(
                    "/runs/${seeded.ortRunId}/detected-licenses/Apache-2.0/packages?sort=-purl"
                )

                response.status shouldBe HttpStatusCode.OK
                response.body<PagedResponse<PackageIdentifier>>() shouldBe PagedResponse(
                    data = listOf(
                        PackageIdentifier(
                            seeded.vcsIdentifier.mapToApi(),
                            seeded.vcsPurl
                        ),
                        PackageIdentifier(
                            seeded.artifactIdentifier.mapToApi(),
                            seeded.artifactPurl
                        )
                    ),
                    pagination = PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("purl", SortDirection.DESCENDING))
                    )
                )
            }
        }

        "return 200 with empty data for a license not in this run" {
            licenseFindingTestApplication { client ->
                val response = client.get(
                    "/runs/${seeded.ortRunId}/detected-licenses/Zlib/packages"
                )

                response.status shouldBe HttpStatusCode.OK
                response.body<PagedResponse<PackageIdentifier>>() shouldBe PagedResponse(
                    data = emptyList(),
                    pagination = PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 0,
                        sortProperties = listOf(SortProperty("identifier", SortDirection.ASCENDING))
                    )
                )
            }
        }
    }
})
