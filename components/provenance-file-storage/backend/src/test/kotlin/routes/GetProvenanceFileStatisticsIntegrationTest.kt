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

package org.eclipse.apoapsis.ortserver.components.provenancefilestorage.routes

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileStatistics
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileStorageIntegrationTest
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileType
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.artifactEntry
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.vcsEntry

class GetProvenanceFileStatisticsIntegrationTest : ProvenanceFileStorageIntegrationTest({
    val repo = vcsEntry("https://example.org/a.git", "rev1", 100)
    val artifact = artifactEntry("https://example.org/c.zip", "0123abcd", 200)

    "GetProvenanceFileStatistics" should {
        ProvenanceFileType.entries.forEach { type ->
            "return the statistics for '${type.pathSegment}'" {
                provenanceFileStorageTestApplication { client ->
                    addEntries(type, repo, artifact)

                    val response = client.get("/admin/${type.pathSegment}/statistics")

                    response shouldHaveStatus HttpStatusCode.OK
                    with(response.body<ProvenanceFileStatistics>()) {
                        totalCount shouldBe 2
                        totalSizeInBytes shouldBe 300
                        largest?.url shouldBe "https://example.org/c.zip"
                    }
                }
            }
        }

        "refresh the cached listing if requested" {
            provenanceFileStorageTestApplication { client ->
                addEntries(ProvenanceFileType.FILE_LIST, repo)
                client.get("/admin/file-lists/statistics")
                addEntries(ProvenanceFileType.FILE_LIST, artifact)

                client.get("/admin/file-lists/statistics").body<ProvenanceFileStatistics>().totalCount shouldBe 1
                client.get("/admin/file-lists/statistics?refresh=true").body<ProvenanceFileStatistics>()
                    .totalCount shouldBe 2
            }
        }

        "return BadRequest for an invalid refresh parameter" {
            provenanceFileStorageTestApplication { client ->
                client.get("/admin/file-lists/statistics?refresh=1") shouldHaveStatus HttpStatusCode.BadRequest
            }
        }
    }
})
