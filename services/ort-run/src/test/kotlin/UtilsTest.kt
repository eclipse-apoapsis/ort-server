/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.services.ortrun

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.PackageCurationProviderConfig
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedPackageCurations
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCuration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCurationData

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.FileList as ModelFileList
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.scanner.FileList as ScannerFileList
import org.ossreviewtoolkit.scanner.utils.FileListResolver

class UtilsTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var db: Database
    lateinit var fixtures: Fixtures

    beforeEach {
        db = dbExtension.db
        fixtures = dbExtension.fixtures
    }

    "getFileLists" should {
        "return the expected file lists" {
            val provenance1 = RepositoryProvenance(VcsInfo(VcsType.GIT, "url", "revision"), "resolvedRevision")
            val provenance2 = ArtifactProvenance(RemoteArtifact("url", Hash.NONE))

            val fileList1 = ScannerFileList(
                ignorePatterns = setOf("ignorePattern1"),
                files = setOf(ScannerFileList.FileEntry("path1", "sha1"))
            )
            val fileList2 = ScannerFileList(
                ignorePatterns = setOf("ignorePattern2"),
                files = setOf(ScannerFileList.FileEntry("path2", "sha1"))
            )

            val fileListResolver = mockk<FileListResolver> {
                every { get(provenance1) } returns fileList1
                every { get(provenance2) } returns fileList2
            }

            val fileLists = getFileLists(fileListResolver, setOf(provenance1, provenance2))

            fileLists should containExactlyInAnyOrder(
                ModelFileList(
                    provenance1,
                    setOf(ModelFileList.Entry("path1", "sha1"))
                ),
                ModelFileList(
                    provenance2,
                    setOf(ModelFileList.Entry("path2", "sha1"))
                )
            )
        }

        "ignore provenances without stored file lists" {
            val provenance1 = RepositoryProvenance(VcsInfo(VcsType.GIT, "url", "revision"), "resolvedRevision")
            val provenance2 = ArtifactProvenance(RemoteArtifact("url", Hash.NONE))

            val fileListResolver = mockk<FileListResolver> {
                every { get(any<KnownProvenance>()) } returns null
            }

            val fileLists = getFileLists(fileListResolver, setOf(provenance1, provenance2))

            fileLists should beEmpty()
        }
    }

    "getPurlByIdentifierIdForOrtRun" should {
        "return base purls and override them with highest-priority curated purls" {
            val ortRun = fixtures.createOrtRun(fixtures.createRepository().id)
            val analyzerJob = fixtures.createAnalyzerJob(ortRun.id)

            val pkg1 = fixtures.generatePackage(Identifier("Maven", "com.example", "base-lib", "1.0.0"))
            val pkg2 = fixtures.generatePackage(Identifier("Maven", "com.example", "curated-lib", "2.0.0"))
            val pkg3 = fixtures.generatePackage(Identifier("Maven", "com.example", "curated-only", "3.0.0"))

            fixtures.createAnalyzerRun(analyzerJob.id, packages = setOf(pkg1, pkg2, pkg3))

            val higherPriorityCurations = ResolvedPackageCurations(
                provider = PackageCurationProviderConfig("provider1"),
                curations = listOf(
                    PackageCuration(
                        id = pkg2.identifier,
                        data = PackageCurationData(purl = "curated-high")
                    ),
                    PackageCuration(
                        id = pkg3.identifier,
                        data = PackageCurationData(purl = "curated-only-high")
                    )
                )
            )

            val lowerPriorityCurations = ResolvedPackageCurations(
                provider = PackageCurationProviderConfig("provider2"),
                curations = listOf(
                    PackageCuration(
                        id = pkg2.identifier,
                        data = PackageCurationData(purl = "curated-low")
                    ),
                    PackageCuration(
                        id = pkg3.identifier,
                        data = PackageCurationData(purl = "curated-only-low")
                    )
                )
            )

            fixtures.resolvedConfigurationRepository.addPackageCurations(
                ortRun.id,
                listOf(higherPriorityCurations, lowerPriorityCurations)
            )

            val identifierIds = db.dbQuery {
                IdentifiersTable
                    .innerJoin(PackagesTable)
                    .innerJoin(PackagesAnalyzerRunsTable)
                    .innerJoin(AnalyzerRunsTable)
                    .innerJoin(AnalyzerJobsTable)
                    .select(
                        IdentifiersTable.id,
                        IdentifiersTable.type,
                        IdentifiersTable.namespace,
                        IdentifiersTable.name,
                        IdentifiersTable.version
                    )
                    .where { AnalyzerJobsTable.ortRunId eq ortRun.id }
                    .associate { row ->
                        Identifier(
                            type = row[IdentifiersTable.type],
                            namespace = row[IdentifiersTable.namespace],
                            name = row[IdentifiersTable.name],
                            version = row[IdentifiersTable.version]
                        ) to row[IdentifiersTable.id].value
                    }
            }

            val purls = db.dbQuery {
                getPurlByIdentifierIdForOrtRun(ortRun.id, identifierIds.values)
            }

            purls shouldBe mapOf(
                identifierIds.getValue(pkg1.identifier) to pkg1.purl,
                identifierIds.getValue(pkg2.identifier) to "curated-high",
                identifierIds.getValue(pkg3.identifier) to "curated-only-high"
            )
        }

        "return an empty map for empty identifier IDs" {
            getPurlByIdentifierIdForOrtRun(ortRunId = 1L, identifierIds = emptyList()) shouldBe emptyMap()
        }
    }
})
