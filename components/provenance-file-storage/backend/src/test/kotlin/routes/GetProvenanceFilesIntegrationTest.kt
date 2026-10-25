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
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFile
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileKind
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileStorageIntegrationTest
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileType
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.artifactEntry
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.toProvenanceFileId
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.vcsEntry
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty

class GetProvenanceFilesIntegrationTest : ProvenanceFileStorageIntegrationTest({
    val repoA = vcsEntry("https://example.org/a.git", "rev1", 100, Instant.fromEpochSeconds(3000))
    val repoB = vcsEntry("https://example.org/b.git", "rev2", 300, Instant.fromEpochSeconds(1000))
    val artifact = artifactEntry("https://example.org/c.zip", "0123abcd", 200, Instant.fromEpochSeconds(2000))

    "GetProvenanceFiles" should {
        ProvenanceFileType.entries.forEach { type ->
            "return the stored files for '${type.pathSegment}'" {
                provenanceFileStorageTestApplication { client ->
                    addEntries(type, repoA, artifact)

                    val response = client.get("/admin/${type.pathSegment}")

                    response shouldHaveStatus HttpStatusCode.OK
                    response.body<PagedResponse<ProvenanceFile>>() shouldBe PagedResponse(
                        listOf(
                            ProvenanceFile(
                                id = repoA.key.toProvenanceFileId(),
                                key = repoA.key.key,
                                kind = ProvenanceFileKind.VCS,
                                url = "https://example.org/a.git",
                                vcsType = "Git",
                                revision = "rev1",
                                hashValue = null,
                                hashAlgorithm = null,
                                sizeInBytes = 100,
                                lastModified = Instant.fromEpochSeconds(3000)
                            ),
                            ProvenanceFile(
                                id = artifact.key.toProvenanceFileId(),
                                key = artifact.key.key,
                                kind = ProvenanceFileKind.SOURCE_ARTIFACT,
                                url = "https://example.org/c.zip",
                                vcsType = null,
                                revision = null,
                                hashValue = "0123abcd",
                                hashAlgorithm = "SHA-1",
                                sizeInBytes = 200,
                                lastModified = Instant.fromEpochSeconds(2000)
                            )
                        ),
                        PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 2,
                            sortProperties = listOf(SortProperty("url", SortDirection.ASCENDING))
                        )
                    )
                }
            }
        }

        "only return the files of the requested type" {
            provenanceFileStorageTestApplication { client ->
                addEntries(ProvenanceFileType.FILE_ARCHIVE, repoA)
                addEntries(ProvenanceFileType.FILE_LIST, repoB)

                val response = client.get("/admin/file-lists")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<PagedResponse<ProvenanceFile>>().data.shouldBeSingleton {
                    it.url shouldBe "https://example.org/b.git"
                }
            }
        }

        "apply the filter, sorting, and paging parameters" {
            provenanceFileStorageTestApplication { client ->
                addEntries(ProvenanceFileType.FILE_ARCHIVE, repoA, repoB, artifact)

                val response = client.get("/admin/file-archives?filter=.GIT&sort=-sizeInBytes&limit=1&offset=1")

                response shouldHaveStatus HttpStatusCode.OK
                with(response.body<PagedResponse<ProvenanceFile>>()) {
                    data.map { it.url } should containExactly("https://example.org/a.git")
                    pagination shouldBe PagingData(
                        limit = 1,
                        offset = 1,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("sizeInBytes", SortDirection.DESCENDING))
                    )
                }
            }
        }

        "refresh the cached listing if requested" {
            provenanceFileStorageTestApplication { client ->
                addEntries(ProvenanceFileType.FILE_ARCHIVE, repoA)
                client.get("/admin/file-archives")
                addEntries(ProvenanceFileType.FILE_ARCHIVE, repoB)

                client.get("/admin/file-archives").body<PagedResponse<ProvenanceFile>>()
                    .pagination.totalCount shouldBe 1
                client.get("/admin/file-archives?refresh=true").body<PagedResponse<ProvenanceFile>>()
                    .pagination.totalCount shouldBe 2
            }
        }

        "return BadRequest for an invalid refresh parameter" {
            provenanceFileStorageTestApplication { client ->
                client.get("/admin/file-archives?refresh=maybe") shouldHaveStatus HttpStatusCode.BadRequest
            }
        }
    }
})
