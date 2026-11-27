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

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileStorageIntegrationTest
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileType
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.toProvenanceFileId
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.vcsEntry
import org.eclipse.apoapsis.ortserver.storage.Key

class DownloadProvenanceFileIntegrationTest : ProvenanceFileStorageIntegrationTest({
    val repo = vcsEntry("https://example.org/repo.git", "0123456789abcdef", 12)
    val data = "file content".toByteArray()

    "DownloadProvenanceFile" should {
        listOf(
            Triple(ProvenanceFileType.FILE_ARCHIVE, ContentType.Application.Zip, "repo-0123456789abcdef.zip"),
            Triple(ProvenanceFileType.FILE_LIST, ContentType.Application.Yaml, "repo-0123456789abcdef.yml")
        ).forEach { (type, contentType, fileName) ->
            "download an existing file from '${type.pathSegment}'" {
                provenanceFileStorageTestApplication { client ->
                    addEntries(type, repo)
                    providerFor(type).contents[repo.key] = data

                    val response = client.get("/admin/${type.pathSegment}/${repo.key.toProvenanceFileId()}/download")

                    response shouldHaveStatus HttpStatusCode.OK
                    response.contentType() shouldBe contentType
                    response.headers[HttpHeaders.ContentDisposition] shouldBe "attachment; filename=$fileName"
                    response.bodyAsBytes() shouldBe data
                }
            }
        }

        "not download files of another type" {
            provenanceFileStorageTestApplication { client ->
                addEntries(ProvenanceFileType.FILE_ARCHIVE, repo)

                client.get("/admin/file-lists/${repo.key.toProvenanceFileId()}/download") shouldHaveStatus
                        HttpStatusCode.NotFound
            }
        }

        "return NotFound if the file does not exist" {
            provenanceFileStorageTestApplication { client ->
                client.get("/admin/file-archives/${Key("nonExisting").toProvenanceFileId()}/download") shouldHaveStatus
                        HttpStatusCode.NotFound
            }
        }

        "return BadRequest for an invalid ID" {
            provenanceFileStorageTestApplication { client ->
                client.get("/admin/file-archives/not-base64!/download") shouldHaveStatus HttpStatusCode.BadRequest
            }
        }
    }
})
