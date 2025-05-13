/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.scanner

import io.kotest.common.runBlocking
import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenanceDao
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToModel

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
            nestedProvenanceStorage = OrtServerNestedProvenanceStorage(
                dbExtension.db,
                packageProvenanceCache,
                ""
            )

            packageProvenanceStorage.writeProvenance(id, vcsInfo, packageProvenance)
        }

        /**
         * Verify that the provided [result] was associated to the given [provenance].
         */
        fun verifyAssociatedProvenance(
            result: NestedProvenanceResolutionResult,
            provenance: RepositoryProvenance = packageProvenance.provenance
        ) {
            transaction {
                val packageProvenanceId = runBlocking {
                    packageProvenanceCache.get(provenance).first()
                }

                val associatedResult = PackageProvenanceDao[packageProvenanceId].nestedProvenance?.mapToOrt()

                associatedResult shouldBe result
            }
        }

        "writeNestedProvenance" should {
            "store a nested provenance in the database and associate it with the package provenance" {
                val result = NestedProvenanceResolutionResult(
                    nestedProvenance = createNestedProvenance(rootProvenance),
                    hasOnlyFixedRevisions = true
                )

                nestedProvenanceStorage.writeNestedProvenance(rootProvenance, result)

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

                nestedProvenanceStorage.writeNestedProvenance(rootProvenance, result1)
                nestedProvenanceStorage.writeNestedProvenance(rootProvenance, result2)

                transaction {
                    NestedProvenancesTable.selectAll().count() shouldBe 2
                }
            }

            "associate a nested provenance with pending package provenances" {
                val subInfo1 = vcsInfo.copy(path = "sub1")
                val subProvenance1 = RepositoryProvenance(subInfo1, subInfo1.revision)
                val subInfo2 = vcsInfo.copy(path = "sub2")
                val subProvenance2 = RepositoryProvenance(subInfo2, subInfo2.revision)

                packageProvenanceStorage.writeProvenance(
                    id.copy(name = "sub1"),
                    subInfo1,
                    ResolvedRepositoryProvenance(subProvenance1, subInfo1.revision, true)
                )
                packageProvenanceStorage.writeProvenance(
                    id.copy(name = "sub2"),
                    subInfo2,
                    ResolvedRepositoryProvenance(subProvenance2, subInfo2.revision, true)
                )

                val result = NestedProvenanceResolutionResult(
                    nestedProvenance = createNestedProvenance(rootProvenance),
                    hasOnlyFixedRevisions = true
                )

                nestedProvenanceStorage.writeNestedProvenance(rootProvenance, result)

                listOf(subProvenance1, subProvenance2).forAll { provenance ->
                    verifyAssociatedProvenance(result, provenance)
                }
            }

            "associate multiple identifiers with the same provenance with a nested provenance" {
                val id1 = createIdentifier()
                val id2 = id1.copy(name = "another-project")
                val vcsInfo = createVcsInfo()
                val provenance = createRepositoryProvenance(vcsInfo)
                val repositoryProvenance = RepositoryProvenance(vcsInfo, vcsInfo.revision)

                packageProvenanceStorage.writeProvenance(id1, vcsInfo, provenance)
                packageProvenanceStorage.writeProvenance(id2, vcsInfo, provenance)

                val result = NestedProvenanceResolutionResult(
                    nestedProvenance = createNestedProvenance(repositoryProvenance),
                    hasOnlyFixedRevisions = true
                )

                nestedProvenanceStorage.writeNestedProvenance(repositoryProvenance, result)

                transaction {
                    val packageProvenances = PackageProvenancesTable.selectAll().toList()
                    packageProvenances shouldHaveSize 3

                    packageProvenances.forAll { row ->
                        val packageProvenanceDao = PackageProvenanceDao.wrapRow(row)
                        packageProvenanceDao.nestedProvenance?.rootVcs?.mapToModel() shouldBe vcsInfo.mapToModel()
                    }
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

                nestedProvenanceStorage.writeNestedProvenance(rootProvenance, result1)
                nestedProvenanceStorage.writeNestedProvenance(rootProvenance, result2)

                nestedProvenanceStorage.readNestedProvenance(rootProvenance) shouldBe result2
            }

            "read the latest nested provenance and associate it with pending package provenances" {
                val resolutionResult = NestedProvenanceResolutionResult(
                    nestedProvenance = createNestedProvenance(rootProvenance),
                    hasOnlyFixedRevisions = true
                )
                nestedProvenanceStorage.writeNestedProvenance(rootProvenance, resolutionResult)

                // Recreate the test storage instances with a clean cache.
                packageProvenanceCache = PackageProvenanceCache()
                packageProvenanceStorage =
                    OrtServerPackageProvenanceStorage(dbExtension.db, scannerRun.id, packageProvenanceCache)
                nestedProvenanceStorage = OrtServerNestedProvenanceStorage(
                    dbExtension.db,
                    packageProvenanceCache,
                    ""
                )

                val subInfo1 = vcsInfo.copy(path = "sub1")
                val subProvenance1 = RepositoryProvenance(subInfo1, subInfo1.revision)
                val subInfo2 = vcsInfo.copy(path = "sub2")
                val subProvenance2 = RepositoryProvenance(subInfo2, subInfo2.revision)

                packageProvenanceStorage.writeProvenance(
                    id.copy(name = "sub1"),
                    subInfo1,
                    ResolvedRepositoryProvenance(subProvenance1, subInfo1.revision, true)
                )
                packageProvenanceStorage.writeProvenance(
                    id.copy(name = "sub2"),
                    subInfo2,
                    ResolvedRepositoryProvenance(subProvenance2, subInfo2.revision, true)
                )

                nestedProvenanceStorage.readNestedProvenance(rootProvenance) shouldBe resolutionResult

                listOf(subProvenance1, subProvenance2).forAll { provenance ->
                    verifyAssociatedProvenance(resolutionResult, provenance)
                }
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
            vcsInfo = vcsInfo
        ),
        clonedRevision = vcsInfo.revision,
        isFixedRevision = true
    )

private fun createNestedProvenance(
    root: KnownProvenance,
    subRepositories: Map<String, RepositoryProvenance> = emptyMap()
) = NestedProvenance(root, subRepositories)
