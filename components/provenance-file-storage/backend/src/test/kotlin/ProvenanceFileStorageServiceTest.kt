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

package org.eclipse.apoapsis.ortserver.components.provenancefilestorage

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.StorageEntryInfo

class ProvenanceFileStorageServiceTest : WordSpec({
    val repoA = vcsEntry("https://example.org/a.git", "rev1", 100, Instant.fromEpochSeconds(3000))
    val repoB = vcsEntry("https://example.org/b.git", "rev2", 300, Instant.fromEpochSeconds(1000))
    val artifact = artifactEntry("https://example.org/c.zip", "0123abcd", 200, Instant.fromEpochSeconds(2000))
    val unknown = StorageEntryInfo(Key("some-unknown-key"), 50, contentType = null, lastModified = null)

    fun createService(vararg entries: StorageEntryInfo): ProvenanceFileStorageService {
        val (archiveStorage, _) = createFakeStorage(*entries)
        val (listStorage, _) = createFakeStorage()
        return ProvenanceFileStorageService(archiveStorage, listStorage)
    }

    "list" should {
        "convert storage entries to provenance files" {
            val service = createService(repoA, artifact, unknown)

            val result = service.list(ProvenanceFileType.FILE_ARCHIVE, null, ListQueryParameters.DEFAULT)

            result.data shouldContainExactly listOf(
                ProvenanceFile(
                    id = unknown.key.toProvenanceFileId(),
                    key = "some-unknown-key",
                    kind = ProvenanceFileKind.UNKNOWN,
                    url = null,
                    vcsType = null,
                    revision = null,
                    hashValue = null,
                    hashAlgorithm = null,
                    sizeInBytes = 50,
                    lastModified = null
                ),
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
            )
            result.totalCount shouldBe 3
        }

        "use the storage for the requested type" {
            val (archiveStorage, _) = createFakeStorage(repoA)
            val (listStorage, _) = createFakeStorage(repoB)
            val service = ProvenanceFileStorageService(archiveStorage, listStorage)

            service.list(ProvenanceFileType.FILE_LIST, null, ListQueryParameters.DEFAULT)
                .data.shouldBeSingleton { it.key shouldBe repoB.key.key }
        }

        "filter files ignoring case" {
            val service = createService(repoA, repoB, artifact)

            suspend fun keysFor(filter: String) =
                service.list(ProvenanceFileType.FILE_ARCHIVE, filter, ListQueryParameters.DEFAULT).data.map { it.key }

            keysFor("B.GIT") should containExactly(repoB.key.key)
            keysFor("rev1") should containExactly(repoA.key.key)
            keysFor("0123ABCD") should containExactly(artifact.key.key)
            keysFor("nothing") should beEmpty()
        }

        "report the number of files matching the filter as total count" {
            val service = createService(repoA, repoB, artifact)

            val result = service.list(ProvenanceFileType.FILE_ARCHIVE, ".git", ListQueryParameters(limit = 1))

            result.data.shouldBeSingleton()
            result.totalCount shouldBe 2
        }

        "sort files by the requested fields" {
            val service = createService(repoA, repoB, artifact)

            suspend fun urlsSortedBy(field: String, direction: OrderDirection) =
                service.list(
                    ProvenanceFileType.FILE_ARCHIVE,
                    null,
                    ListQueryParameters(sortFields = listOf(OrderField(field, direction)))
                ).data.map { it.url }

            urlsSortedBy("url", OrderDirection.DESCENDING) should containExactly(
                "https://example.org/c.zip",
                "https://example.org/b.git",
                "https://example.org/a.git"
            )
            urlsSortedBy("sizeInBytes", OrderDirection.ASCENDING) should containExactly(
                "https://example.org/a.git",
                "https://example.org/c.zip",
                "https://example.org/b.git"
            )
            urlsSortedBy("lastModified", OrderDirection.DESCENDING) should containExactly(
                "https://example.org/a.git",
                "https://example.org/c.zip",
                "https://example.org/b.git"
            )
        }

        "apply offset and limit" {
            val service = createService(repoA, repoB, artifact)

            val result = service.list(ProvenanceFileType.FILE_ARCHIVE, null, ListQueryParameters(limit = 1, offset = 1))

            result.data.map { it.url } should containExactly("https://example.org/b.git")
            result.params shouldBe ListQueryParameters(limit = 1, offset = 1)
        }

        "return an empty page for an offset beyond the number of files" {
            val service = createService(repoA)

            val result = service.list(ProvenanceFileType.FILE_ARCHIVE, null, ListQueryParameters(offset = 10))

            result.data should beEmpty()
            result.totalCount shouldBe 1
        }

        "throw an exception for unsupported sort fields" {
            val service = createService(repoA)

            shouldThrow<QueryParametersException> {
                service.list(
                    ProvenanceFileType.FILE_ARCHIVE,
                    null,
                    ListQueryParameters(sortFields = listOf(OrderField("unknown", OrderDirection.ASCENDING)))
                )
            }
        }
    }

    "delete" should {
        "delete an existing file" {
            val (archiveStorage, archiveProvider) = createFakeStorage(repoA, repoB)
            val (listStorage, _) = createFakeStorage()
            val service = ProvenanceFileStorageService(archiveStorage, listStorage)

            service.delete(ProvenanceFileType.FILE_ARCHIVE, repoA.key) shouldBe true

            archiveProvider.entries.keys should containExactly(repoB.key)
        }

        "return false for a non-existing file" {
            val service = createService(repoA)

            service.delete(ProvenanceFileType.FILE_ARCHIVE, Key("nonExisting")) shouldBe false
        }
    }
})
