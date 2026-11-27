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
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.Storage.Companion.dataString
import org.eclipse.apoapsis.ortserver.storage.StorageEntryInfo
import org.eclipse.apoapsis.ortserver.storage.StorageException

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

    "caching" should {
        "reuse the listing of a storage for subsequent requests" {
            val (archiveStorage, archiveProvider) = createFakeStorage(repoA, repoB)
            val (listStorage, _) = createFakeStorage()
            val service = ProvenanceFileStorageService(archiveStorage, listStorage)

            service.list(ProvenanceFileType.FILE_ARCHIVE, null, ListQueryParameters(limit = 1))
            service.list(ProvenanceFileType.FILE_ARCHIVE, null, ListQueryParameters(limit = 1, offset = 1))
            service.statistics(ProvenanceFileType.FILE_ARCHIVE)

            archiveProvider.listCount shouldBe 1
        }

        "list the storage again if a refresh is requested" {
            val (archiveStorage, archiveProvider) = createFakeStorage(repoA)
            val (listStorage, _) = createFakeStorage()
            val clock = FixedClock(Instant.fromEpochSeconds(1000))
            val service = ProvenanceFileStorageService(archiveStorage, listStorage, clock)
            service.list(ProvenanceFileType.FILE_ARCHIVE, null, ListQueryParameters.DEFAULT)

            archiveProvider.entries[repoB.key] = repoB
            clock.now = Instant.fromEpochSeconds(1001)

            service.list(ProvenanceFileType.FILE_ARCHIVE, null, ListQueryParameters.DEFAULT, refresh = true)
                .totalCount shouldBe 2
            service.statistics(ProvenanceFileType.FILE_ARCHIVE).totalCount shouldBe 2
            archiveProvider.listCount shouldBe 2
        }

        "reflect deletions without listing the storage again" {
            val (archiveStorage, archiveProvider) = createFakeStorage(repoA, repoB)
            val (listStorage, _) = createFakeStorage()
            val service = ProvenanceFileStorageService(archiveStorage, listStorage)
            service.list(ProvenanceFileType.FILE_ARCHIVE, null, ListQueryParameters.DEFAULT)

            service.delete(ProvenanceFileType.FILE_ARCHIVE, repoA.key)

            service.list(ProvenanceFileType.FILE_ARCHIVE, null, ListQueryParameters.DEFAULT)
                .data.shouldBeSingleton { it.key shouldBe repoB.key.key }
            service.statistics(ProvenanceFileType.FILE_ARCHIVE).totalCount shouldBe 1
            archiveProvider.listCount shouldBe 1
        }

        "keep the cached listing if a deletion fails" {
            val (archiveStorage, archiveProvider) = createFakeStorage(repoA, repoB)
            val (listStorage, _) = createFakeStorage()
            val service = ProvenanceFileStorageService(archiveStorage, listStorage)
            service.list(ProvenanceFileType.FILE_ARCHIVE, null, ListQueryParameters.DEFAULT)
            archiveProvider.deleteFailure = IllegalStateException("Test exception: Access denied.")

            shouldThrow<StorageException> {
                service.delete(ProvenanceFileType.FILE_ARCHIVE, repoA.key)
            }

            service.statistics(ProvenanceFileType.FILE_ARCHIVE).totalCount shouldBe 2
            archiveProvider.listCount shouldBe 1
        }

        "use separate caches for the storages" {
            val (archiveStorage, archiveProvider) = createFakeStorage(repoA)
            val (listStorage, listProvider) = createFakeStorage(repoB)
            val service = ProvenanceFileStorageService(archiveStorage, listStorage)

            service.statistics(ProvenanceFileType.FILE_ARCHIVE)
            service.statistics(ProvenanceFileType.FILE_LIST)

            archiveProvider.listCount shouldBe 1
            listProvider.listCount shouldBe 1
        }
    }

    "statistics" should {
        "compute the statistics for all files of the requested type" {
            val (archiveStorage, _) = createFakeStorage(repoA, repoB)
            val (listStorage, _) = createFakeStorage(artifact)
            val now = Instant.fromEpochSeconds(5000)
            val service = ProvenanceFileStorageService(archiveStorage, listStorage, FixedClock(now))

            val statistics = service.statistics(ProvenanceFileType.FILE_ARCHIVE)

            statistics.snapshotTime shouldBe now
            statistics.totalCount shouldBe 2
            statistics.totalSizeInBytes shouldBe 400
        }
    }

    "read" should {
        "return the contents of an existing file" {
            val (archiveStorage, archiveProvider) = createFakeStorage(repoA)
            val (listStorage, _) = createFakeStorage()
            archiveProvider.contents[repoA.key] = "archive data".toByteArray()
            val service = ProvenanceFileStorageService(archiveStorage, listStorage)

            service.read(ProvenanceFileType.FILE_ARCHIVE, repoA.key).shouldNotBeNull().use { entry ->
                entry.dataString shouldBe "archive data"
            }
        }

        "return null for a non-existing file" {
            val service = createService(repoA)

            service.read(ProvenanceFileType.FILE_ARCHIVE, Key("nonExisting")) should beNull()
        }

        "return null for a file of another type" {
            val service = createService(repoA)

            service.read(ProvenanceFileType.FILE_LIST, repoA.key) should beNull()
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
