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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import org.eclipse.apoapsis.ortserver.components.licensefindings.LicenseFindingIntegrationTest
import org.eclipse.apoapsis.ortserver.components.licensefindings.SeedResult
import org.eclipse.apoapsis.ortserver.components.licensefindings.addLicenseFindings
import org.eclipse.apoapsis.ortserver.components.licensefindings.seedData

class GetRunDetectedLicensesForIdentifierIntegrationTest : LicenseFindingIntegrationTest({
    lateinit var seeded: SeedResult

    beforeEach {
        seeded = seedData(dbExtension.fixtures, dbExtension.db)
    }

    "GetRunDetectedLicensesForIdentifier" should {
        "return the detected licenses for a URL-encoded package identifier" {
            val identifier = URLEncoder.encode(
                seeded.artifactIdentifier.toCoordinates(),
                StandardCharsets.UTF_8
            )

            licenseFindingTestApplication { client ->
                val response = client.get(
                    "/runs/${seeded.ortRunId}/detected-licenses/identifiers/$identifier"
                )

                response.status shouldBe HttpStatusCode.OK
                response.body<List<String>>() should containExactly("Apache-2.0", "BSD-3-Clause")
            }
        }

        "return the detected licenses for a project identifier" {
            addLicenseFindings(
                dbExtension.db,
                seeded.scannerRunId,
                seeded.projectIdentifier,
                listOf("MIT")
            )
            val identifier = URLEncoder.encode(
                seeded.projectIdentifier.toCoordinates(),
                StandardCharsets.UTF_8
            )

            licenseFindingTestApplication { client ->
                val response = client.get(
                    "/runs/${seeded.ortRunId}/detected-licenses/identifiers/$identifier"
                )

                response.status shouldBe HttpStatusCode.OK
                response.body<List<String>>() should containExactly("MIT")
            }
        }

        "return an empty list for an identifier without findings" {
            val identifier = URLEncoder.encode(
                seeded.projectIdentifier.toCoordinates(),
                StandardCharsets.UTF_8
            )

            licenseFindingTestApplication { client ->
                val response = client.get(
                    "/runs/${seeded.ortRunId}/detected-licenses/identifiers/$identifier"
                )

                response.status shouldBe HttpStatusCode.OK
                response.body<List<String>>().shouldBeEmpty()
            }
        }

        "return 404 for an unknown run" {
            val identifier = URLEncoder.encode(
                seeded.artifactIdentifier.toCoordinates(),
                StandardCharsets.UTF_8
            )

            licenseFindingTestApplication { client ->
                val response = client.get("/runs/999999/detected-licenses/identifiers/$identifier")

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})
