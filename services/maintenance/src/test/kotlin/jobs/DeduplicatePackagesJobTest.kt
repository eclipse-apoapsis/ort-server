/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.services.maintenance.jobs

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should

import kotlin.time.Duration.Companion.milliseconds

import org.eclipse.apoapsis.ortserver.dao.disableForeignKeyConstraints
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.AuthorsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.MappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.PackagesAuthorsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.PackagesDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.ProcessedDeclaredLicensesMappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.ProcessedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.UnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.DeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.RemoteArtifactsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.services.maintenance.MaintenanceService

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

@Suppress("ClassNaming")
class DeduplicatePackagesJobTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    var identifier1 = 0L
    var identifier2 = 0L

    var vcs1 = 0L
    var vcs2 = 0L

    var vcsProcessed1 = 0L
    var vcsProcessed2 = 0L

    var binaryArtifact1 = 0L
    var binaryArtifact2 = 0L

    var sourceArtifact1 = 0L
    var sourceArtifact2 = 0L

    suspend fun runJob() {
        val service = MaintenanceService(dbExtension.db, 100.milliseconds)
        service.addJob(DeduplicatePackagesJob(dbExtension.db))
        service.run()
    }

    beforeEach {
        transaction {
            identifier1 = IdentifiersTable.insertAndGetId {
                it[type] = "type1"
                it[namespace] = "namespace1"
                it[name] = "name1"
                it[version] = "version1"
            }.value

            identifier2 = IdentifiersTable.insertAndGetId {
                it[type] = "type2"
                it[namespace] = "namespace2"
                it[name] = "name2"
                it[version] = "version2"
            }.value

            vcs1 = VcsInfoTable.insertAndGetId {
                it[type] = "type1"
                it[url] = "url1"
                it[revision] = "revision1"
                it[path] = "path1"
            }.value

            vcs2 = VcsInfoTable.insertAndGetId {
                it[type] = "type2"
                it[url] = "url2"
                it[revision] = "revision2"
                it[path] = "path2"
            }.value

            vcsProcessed1 = VcsInfoTable.insertAndGetId {
                it[type] = "processed_type1"
                it[url] = "processed_url1"
                it[revision] = "processed_revision1"
                it[path] = "processed_path1"
            }.value

            vcsProcessed2 = VcsInfoTable.insertAndGetId {
                it[type] = "processed_type2"
                it[url] = "processed_url2"
                it[revision] = "processed_revision2"
                it[path] = "processed_path2"
            }.value

            binaryArtifact1 = RemoteArtifactsTable.insertAndGetId {
                it[url] = "binary_url1"
                it[hashValue] = "binary_hashValue1"
                it[hashAlgorithm] = "binary_hashAlgorithm1"
            }.value

            binaryArtifact2 = RemoteArtifactsTable.insertAndGetId {
                it[url] = "binary_url2"
                it[hashValue] = "binary_hashValue2"
                it[hashAlgorithm] = "binary_hashAlgorithm2"
            }.value

            sourceArtifact1 = RemoteArtifactsTable.insertAndGetId {
                it[url] = "source_url1"
                it[hashValue] = "source_hashValue1"
                it[hashAlgorithm] = "source_hashAlgorithm1"
            }.value

            sourceArtifact2 = RemoteArtifactsTable.insertAndGetId {
                it[url] = "source_url2"
                it[hashValue] = "source_hashValue2"
                it[hashAlgorithm] = "source_hashAlgorithm2"
            }.value
        }
    }

    "the migration" should {
        "keep different packages" {
            var pkg1 = 0L
            var pkg2 = 0L
            var pkg3 = 0L
            var pkg4 = 0L
            var pkg5 = 0L
            var pkg6 = 0L

            transaction {
                pkg1 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg2 = createPackage(identifier2, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg3 = createPackage(identifier1, vcs2, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg4 = createPackage(identifier1, vcs1, vcsProcessed2, binaryArtifact1, sourceArtifact1)
                pkg5 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact2, sourceArtifact1)
                pkg6 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact2)
            }

            runJob()

            transaction {
                PackagesTable.selectAll().map { it[PackagesTable.id].value } should
                        containExactly(pkg1, pkg2, pkg3, pkg4, pkg5, pkg6)
            }
        }

        "keep packages with different authors" {
            var pkg1 = 0L
            var pkg2 = 0L
            var pkg3 = 0L

            var author1: Long
            var author2: Long

            transaction {
                pkg1 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg2 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg3 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)

                author1 = createAuthor("author1")
                author2 = createAuthor("author2")

                addAuthors(pkg1, author1)
                addAuthors(pkg2, author2)
                addAuthors(pkg3, author1, author2)
            }

            runJob()

            transaction {
                PackagesTable.selectAll().map { it[PackagesTable.id].value } should containExactly(pkg1, pkg2, pkg3)
            }
        }

        "keep packages with different declared licenses" {
            var pkg1 = 0L
            var pkg2 = 0L
            var pkg3 = 0L

            var license1: Long
            var license2: Long

            transaction {
                pkg1 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg2 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg3 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)

                license1 = createDeclaredLicense("license1")
                license2 = createDeclaredLicense("license2")

                addDeclaredLicenses(pkg1, license1)
                addDeclaredLicenses(pkg2, license2)
                addDeclaredLicenses(pkg3, license1, license2)
            }

            runJob()

            transaction {
                PackagesTable.selectAll().map { it[PackagesTable.id].value } should containExactly(pkg1, pkg2, pkg3)
            }
        }

        "keep packages with different processed declared licenses" {
            var pkg1 = 0L
            var pkg2 = 0L
            var pkg3 = 0L
            var pkg4 = 0L
            var pkg5 = 0L
            var pkg6 = 0L
            var pkg7 = 0L

            val license1 = "license1"
            val license2 = "license2"

            var mappedLicense1: Long
            var mappedLicense2: Long

            var unmappedLicense1: Long
            var unmappedLicense2: Long

            transaction {
                pkg1 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg2 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg3 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg4 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg5 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg6 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg7 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)

                mappedLicense1 = createMappedDeclaredLicense("license1", "mapped1")
                mappedLicense2 = createMappedDeclaredLicense("license2", "mapped2")

                unmappedLicense1 = createUnmappedDeclaredLicense("unmapped1")
                unmappedLicense2 = createUnmappedDeclaredLicense("unmapped2")

                createProcessedDeclaredLicense(pkg1, license1, listOf(mappedLicense1), listOf(unmappedLicense1))
                createProcessedDeclaredLicense(pkg2, null, listOf(mappedLicense1), listOf(unmappedLicense1))
                createProcessedDeclaredLicense(pkg3, license2, listOf(mappedLicense1), listOf(unmappedLicense1))
                createProcessedDeclaredLicense(pkg4, license1, emptyList(), listOf(unmappedLicense1))
                createProcessedDeclaredLicense(pkg5, license1, listOf(mappedLicense2), listOf(unmappedLicense1))
                createProcessedDeclaredLicense(pkg6, license1, listOf(mappedLicense1), emptyList())
                createProcessedDeclaredLicense(pkg7, license1, listOf(mappedLicense1), listOf(unmappedLicense2))
            }

            runJob()

            transaction {
                PackagesTable.selectAll().map { it[PackagesTable.id].value } should
                        containExactly(pkg1, pkg2, pkg3, pkg4, pkg5, pkg6, pkg7)
            }
        }

        "remove duplicate packages" {
            var pkg1 = 0L
            var pkg2 = 0L
            var pkg3 = 0L

            var author1: Long
            var author2: Long

            var license1: Long
            var license2: Long

            val spdxExpression = "license1 AND license2"

            var mappedLicense1: Long
            var mappedLicense2: Long

            var unmappedLicense1: Long
            var unmappedLicense2: Long

            transaction {
                pkg1 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg2 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg3 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)

                author1 = createAuthor("author1")
                author2 = createAuthor("author2")

                addAuthors(pkg1, author1, author2)
                addAuthors(pkg2, author1, author2)
                addAuthors(pkg3, author2, author1)

                license1 = createDeclaredLicense("license1")
                license2 = createDeclaredLicense("license2")

                addDeclaredLicenses(pkg1, license1, license2)
                addDeclaredLicenses(pkg2, license1, license2)
                addDeclaredLicenses(pkg3, license2, license1)

                mappedLicense1 = createMappedDeclaredLicense("license1", "mapped1")
                mappedLicense2 = createMappedDeclaredLicense("license2", "mapped2")

                unmappedLicense1 = createUnmappedDeclaredLicense("unmapped1")
                unmappedLicense2 = createUnmappedDeclaredLicense("unmapped2")

                createProcessedDeclaredLicense(
                    pkg1,
                    spdxExpression,
                    listOf(mappedLicense1, mappedLicense2),
                    listOf(unmappedLicense1, unmappedLicense2)
                )

                createProcessedDeclaredLicense(
                    pkg2,
                    spdxExpression,
                    listOf(mappedLicense1, mappedLicense2),
                    listOf(unmappedLicense1, unmappedLicense2)
                )

                createProcessedDeclaredLicense(
                    pkg3,
                    spdxExpression,
                    listOf(mappedLicense2, mappedLicense1),
                    listOf(unmappedLicense2, unmappedLicense1)
                )
            }

            runJob()

            transaction {
                PackagesTable.selectAll().map { it[PackagesTable.id].value } should containExactly(pkg1)

                ProcessedDeclaredLicensesTable.selectAll()
                    .where { ProcessedDeclaredLicensesTable.packageId inList listOf(pkg2, pkg3) }
                    .toList() should beEmpty()
            }
        }

        "update the association from analyzer runs to packages" {
            val analzyerRun1 = 1L
            val analzyerRun2 = 2L
            val analzyerRun3 = 3L

            var pkg1 = 0L
            var pkg2 = 0L
            var pkg3: Long
            var pkg4: Long
            var pkg5: Long
            var pkg6: Long

            transaction {
                pkg1 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg2 = createPackage(identifier2, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg3 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg4 = createPackage(identifier2, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg5 = createPackage(identifier1, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)
                pkg6 = createPackage(identifier2, vcs1, vcsProcessed1, binaryArtifact1, sourceArtifact1)

                disableForeignKeyConstraints {
                    PackagesAnalyzerRunsTable.insert {
                        it[packageId] = pkg1
                        it[analyzerRunId] = analzyerRun1
                    }

                    PackagesAnalyzerRunsTable.insert {
                        it[packageId] = pkg2
                        it[analyzerRunId] = analzyerRun1
                    }

                    PackagesAnalyzerRunsTable.insert {
                        it[packageId] = pkg3
                        it[analyzerRunId] = analzyerRun2
                    }

                    PackagesAnalyzerRunsTable.insert {
                        it[packageId] = pkg4
                        it[analyzerRunId] = analzyerRun2
                    }

                    PackagesAnalyzerRunsTable.insert {
                        it[packageId] = pkg5
                        it[analyzerRunId] = analzyerRun3
                    }

                    PackagesAnalyzerRunsTable.insert {
                        it[packageId] = pkg6
                        it[analyzerRunId] = analzyerRun3
                    }
                }
            }

            runJob()

            transaction {
                PackagesAnalyzerRunsTable.selectAll()
                    .where { PackagesAnalyzerRunsTable.analyzerRunId eq analzyerRun1 }
                    .map { it[PackagesAnalyzerRunsTable.packageId].value } should containExactly(pkg1, pkg2)

                PackagesAnalyzerRunsTable.selectAll()
                    .where { PackagesAnalyzerRunsTable.analyzerRunId eq analzyerRun2 }
                    .map { it[PackagesAnalyzerRunsTable.packageId].value } should containExactly(pkg1, pkg2)

                PackagesAnalyzerRunsTable.selectAll()
                    .where { PackagesAnalyzerRunsTable.analyzerRunId eq analzyerRun3 }
                    .map { it[PackagesAnalyzerRunsTable.packageId].value } should containExactly(pkg1, pkg2)
            }
        }
    }
})

private fun createPackage(
    identifier: Long,
    vcs: Long,
    vcsProcessed: Long,
    binaryArtifact: Long,
    sourceArtifact: Long
) =
    PackagesTable.insertAndGetId {
        it[identifierId] = identifier
        it[vcsId] = vcs
        it[vcsProcessedId] = vcsProcessed
        it[binaryArtifactId] = binaryArtifact
        it[sourceArtifactId] = sourceArtifact
        it[purl] = "pkg"
        it[cpe] = "cpe"
        it[description] = "description"
        it[homepageUrl] = "homepageUrl"
        it[isMetadataOnly] = false
        it[isModified] = false
    }.value

private fun createAuthor(name: String) = AuthorsTable.insertAndGetId { it[this.name] = name }.value

private fun addAuthors(pkg: Long, vararg authors: Long) =
    authors.forEach { author ->
        PackagesAuthorsTable.insert {
            it[packageId] = pkg
            it[authorId] = author
        }
    }

private fun createDeclaredLicense(license: String) =
    DeclaredLicensesTable.insertAndGetId {
        it[name] = license
    }.value

private fun addDeclaredLicenses(pkg: Long, vararg licenses: Long) =
    licenses.forEach { license ->
        PackagesDeclaredLicensesTable.insert {
            it[packageId] = pkg
            it[declaredLicenseId] = license
        }
    }

private fun createMappedDeclaredLicense(declaredLicense: String, mappedLicense: String) =
    MappedDeclaredLicensesTable.insertAndGetId {
        it[this.declaredLicense] = declaredLicense
        it[this.mappedLicense] = mappedLicense
    }.value

private fun createUnmappedDeclaredLicense(license: String) =
    UnmappedDeclaredLicensesTable.insertAndGetId {
        it[this.unmappedLicense] = license
    }.value

private fun createProcessedDeclaredLicense(
    pkg: Long,
    license: String?,
    mappedLicenses: List<Long>,
    unmappedLicenses: List<Long>
) {
    val processedLicenseId = ProcessedDeclaredLicensesTable.insertAndGetId {
        it[packageId] = pkg
        it[spdxExpression] = license
    }.value

    mappedLicenses.forEach { mappedLicenseId ->
        ProcessedDeclaredLicensesMappedDeclaredLicensesTable.insert {
            it[processedDeclaredLicenseId] = processedLicenseId
            it[mappedDeclaredLicenseId] = mappedLicenseId
        }
    }

    unmappedLicenses.forEach { unmappedLicenseId ->
        ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable.insert {
            it[processedDeclaredLicenseId] = processedLicenseId
            it[unmappedDeclaredLicenseId] = unmappedLicenseId
        }
    }
}
