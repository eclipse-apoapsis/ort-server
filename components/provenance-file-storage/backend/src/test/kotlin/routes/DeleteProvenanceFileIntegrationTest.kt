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
import io.kotest.matchers.should

import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileStorageIntegrationTest
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileType
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.toProvenanceFileId
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.vcsEntry
import org.eclipse.apoapsis.ortserver.storage.Key

class DeleteProvenanceFileIntegrationTest : ProvenanceFileStorageIntegrationTest({
    val repoA = vcsEntry("https://example.org/a.git", "rev1", 100)
    val repoB = vcsEntry("https://example.org/b.git", "rev2", 300)

    "DeleteProvenanceFile" should {
        ProvenanceFileType.entries.forEach { type ->
            "delete an existing file from '${type.pathSegment}'" {
                provenanceFileStorageTestApplication { client ->
                    addEntries(type, repoA, repoB)

                    client.delete("/admin/${type.pathSegment}/${repoA.key.toProvenanceFileId()}") shouldHaveStatus
                            HttpStatusCode.NoContent

                    providerFor(type).entries.keys should containExactly(repoB.key)
                }
            }
        }

        "not delete files of another type" {
            provenanceFileStorageTestApplication { client ->
                addEntries(ProvenanceFileType.FILE_ARCHIVE, repoA)

                client.delete("/admin/file-lists/${repoA.key.toProvenanceFileId()}") shouldHaveStatus
                        HttpStatusCode.NotFound

                fileArchiveProvider.entries.keys should containExactly(repoA.key)
            }
        }

        "return NotFound if the file does not exist" {
            provenanceFileStorageTestApplication { client ->
                client.delete("/admin/file-archives/${Key("nonExisting").toProvenanceFileId()}") shouldHaveStatus
                        HttpStatusCode.NotFound
            }
        }

        "return BadRequest for an invalid ID" {
            provenanceFileStorageTestApplication { client ->
                client.delete("/admin/file-archives/not-base64!") shouldHaveStatus HttpStatusCode.BadRequest
            }
        }
    }
})
