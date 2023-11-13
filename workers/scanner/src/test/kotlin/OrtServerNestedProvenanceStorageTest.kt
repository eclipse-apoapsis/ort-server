/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.scanner

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceResolutionResult
import org.ossreviewtoolkit.scanner.provenance.ResolvedRepositoryProvenance
import org.ossreviewtoolkit.server.dao.tables.provenance.NestedProvenancesTable
import org.ossreviewtoolkit.server.dao.tables.provenance.PackageProvenanceDao
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun

class OrtServerNestedProvenanceStorageTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())
    private val id = createIdentifier()
    private val vcsInfo = createVcsInfo()
    private val packageProvenance = createRepositoryProvenance(vcsInfo)
    private val rootProvenance = packageProvenance.provenance

    private lateinit var packageProvenanceCache: PackageProvenanceCache
    private lateinit var packageProvenanceStorage: OrtServerPackageProvenanceStorage
    private lateinit var nestedProvenanceStorage: OrtServerNestedProvenanceStorage
    private lateinit var scannerRun: ScannerRun

    init {
        beforeEach {
            scannerRun = dbExtension.fixtures.scannerRunRepository.create(dbExtension.fixtures.scannerJob.id)
            packageProvenanceCache = PackageProvenanceCache()
            packageProvenanceStorage =
                OrtServerPackageProvenanceStorage(dbExtension.db, scannerRun.id, packageProvenanceCache)
            nestedProvenanceStorage = OrtServerNestedProvenanceStorage(dbExtension.db, packageProvenanceCache)

            packageProvenanceStorage.putProvenance(id, vcsInfo, packageProvenance)
        }

        /**
         * Verify that the provided [result] was associated to the [packageProvenance].
         */
        fun verifyAssociatedProvenance(result: NestedProvenanceResolutionResult) {
            transaction {
                val packageProvenanceId = packageProvenanceCache.get(packageProvenance.provenance)!!
                val associatedResult = PackageProvenanceDao[packageProvenanceId].nestedProvenance?.mapToOrt()

                associatedResult shouldBe result
            }
        }

        "putNestedProvenance" should {
            "store a nested provenance in the database and associate it with the package provenance" {
                val result = NestedProvenanceResolutionResult(
                    nestedProvenance = createNestedProvenance(rootProvenance),
                    hasOnlyFixedRevisions = true
                )

                nestedProvenanceStorage.putNestedProvenance(rootProvenance, result)

                nestedProvenanceStorage.readNestedProvenance(rootProvenance) shouldBe result

                verifyAssociatedProvenance(result)
            }

            "be able to store multiple results for the same VCS and revision" {
                val result1 = NestedProvenanceResolutionResult(
                    nestedProvenance = createNestedProvenance(rootProvenance, mapOf("path" to rootProvenance)),
                    hasOnlyFixedRevisions = true
                )
                val result2 = NestedProvenanceResolutionResult(
                    nestedProvenance = createNestedProvenance(rootProvenance),
                    hasOnlyFixedRevisions = true
                )

                nestedProvenanceStorage.putNestedProvenance(rootProvenance, result1)
                nestedProvenanceStorage.putNestedProvenance(rootProvenance, result2)

                transaction {
                    NestedProvenancesTable.selectAll().count() shouldBe 2
                }
            }
        }

        "read nested provenance" should {
            "return null if no nested provenance is stored" {
                nestedProvenanceStorage.readNestedProvenance(rootProvenance) should beNull()
            }

            "read the latest nested provenance from the database and associate it with the package provenance" {
                val result1 = NestedProvenanceResolutionResult(
                    nestedProvenance = createNestedProvenance(rootProvenance, mapOf("path" to rootProvenance)),
                    hasOnlyFixedRevisions = true
                )
                val result2 = NestedProvenanceResolutionResult(
                    nestedProvenance = createNestedProvenance(rootProvenance),
                    hasOnlyFixedRevisions = true
                )

                nestedProvenanceStorage.putNestedProvenance(rootProvenance, result1)
                nestedProvenanceStorage.putNestedProvenance(rootProvenance, result2)

                nestedProvenanceStorage.readNestedProvenance(rootProvenance) shouldBe result2
            }
        }
    }
}

private fun createIdentifier() = Identifier("Maven:org.apache.logging.log4j:log4j-api:2.14.1")

private fun createVcsInfo(
    type: VcsType = VcsType.GIT,
    url: String = "https://github.com/apache/logging-log4j2.git",
    revision: String = "be881e503e14b267fb8a8f94b6d15eddba7ed8c4"
) = VcsInfo(type, url, revision)

private fun createRepositoryProvenance(vcsInfo: VcsInfo) =
    ResolvedRepositoryProvenance(
        provenance = RepositoryProvenance(
            resolvedRevision = vcsInfo.revision,
            vcsInfo = VcsInfo(
                path = vcsInfo.path,
                url = vcsInfo.url,
                revision = vcsInfo.revision,
                type = VcsType(vcsInfo.type.toString())
            )
        ),
        clonedRevision = vcsInfo.revision,
        isFixedRevision = true
    )

private fun createNestedProvenance(
    root: KnownProvenance,
    subRepositories: Map<String, RepositoryProvenance> = emptyMap()
) = NestedProvenance(root, subRepositories)
