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

package org.eclipse.apoapsis.ortserver.services

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField

import org.jetbrains.exposed.sql.Database

class PackageServiceTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var db: Database
    private lateinit var fixtures: Fixtures

    init {
        beforeEach {
            db = dbExtension.db
            fixtures = dbExtension.fixtures
        }

        "listForOrtRunId" should {
            "return the packages for the given ORT run id" {
                val service = PackageService(db)

                val pkg1 = Package(
                    Identifier("Maven", "com.example", "example", "1.0"),
                    purl = "pkg:maven/com.example/example@1.0",
                    cpe = null,
                    authors = emptySet(),
                    declaredLicenses = emptySet(),
                    ProcessedDeclaredLicense(
                        spdxExpression = "Expression",
                        mappedLicenses = emptyMap(),
                        unmappedLicenses = emptySet()
                    ),
                    description = "An example package",
                    homepageUrl = "https://example.com",
                    binaryArtifact = RemoteArtifact(
                        "https://example.com/example-1.0.jar",
                        "sha1:binary",
                        "SHA-1"
                    ),
                    sourceArtifact = RemoteArtifact(
                        "https://example.com/example-1.0-sources.jar",
                        "sha1:source",
                        "SHA-1"
                    ),
                    vcs = VcsInfo(
                        RepositoryType(""),
                        "https://example.com/git",
                        "revision1",
                        "path"
                    ),
                    vcsProcessed = VcsInfo(
                        RepositoryType("GIT"),
                        "https://example.com/git",
                        "revision2",
                        "path"
                    ),
                    isMetadataOnly = false,
                    isModified = false
                )

                val pkg2 = Package(
                    Identifier("Maven", "com.example", "example2", "1.0"),
                    purl = "pkg:maven/com.example/example2@1.0",
                    cpe = null,
                    authors = emptySet(),
                    declaredLicenses = emptySet(),
                    ProcessedDeclaredLicense(
                        spdxExpression = "Expression",
                        mappedLicenses = emptyMap(),
                        unmappedLicenses = emptySet()
                    ),
                    description = "Another example package",
                    homepageUrl = "https://example.com",
                    binaryArtifact = RemoteArtifact(
                        "https://example.com/example2-1.0.jar",
                        "sha1:binary",
                        "SHA-1"
                    ),
                    sourceArtifact = RemoteArtifact(
                        "https://example.com/example2-1.0-sources.jar",
                        "sha1:source",
                        "SHA-1"
                    ),
                    vcs = VcsInfo(
                        RepositoryType("GIT"),
                        "https://example.com/git",
                        "revision",
                        "path"
                    ),
                    vcsProcessed = VcsInfo(
                        RepositoryType("GIT"),
                        "https://example.com/git",
                        "revision",
                        "path"
                    ),
                    isMetadataOnly = false,
                    isModified = false
                )

                val ortRunId = createAnalyzerRunWithPackages(setOf(pkg1, pkg2)).id

                service.listForOrtRunId(ortRunId).data should containExactlyInAnyOrder(pkg1, pkg2)
            }

            "return non-empty maps and sets for authors, declared licenses, and mapped and unmapped licenses" {
                val service = PackageService(db)

                val ortRunId = createAnalyzerRunWithPackages(
                    setOf(
                        Package(
                            Identifier("Maven", "com.example", "example", "1.0"),
                            purl = "pkg:maven/com.example/example@1.0",
                            cpe = null,
                            authors = setOf("Author One", "Author Two", "Author Three"),
                            declaredLicenses = setOf("License 1", "License 2", "License 3", "License 4"),
                            ProcessedDeclaredLicense(
                                spdxExpression = "License Expression",
                                mappedLicenses = mapOf(
                                    "License 1" to "Mapped License 1",
                                    "License 2" to "Mapped License 2",
                                    ),
                                unmappedLicenses = setOf("License 1", "License 2", "License 3", "License 4")
                            ),
                            description = "An example package",
                            homepageUrl = "https://example.com",
                            binaryArtifact = RemoteArtifact(
                                "https://example.com/example-1.0.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            sourceArtifact = RemoteArtifact(
                                "https://example.com/example-1.0-sources.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            vcs = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            vcsProcessed = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            isMetadataOnly = false,
                            isModified = false
                        )
                    )
                ).id

                val results = service.listForOrtRunId(ortRunId).data

                results shouldHaveSize 1

                with(results.first()) {
                    authors shouldHaveSize 3
                    authors shouldBe setOf("Author One", "Author Two", "Author Three")
                    declaredLicenses shouldHaveSize 4
                    declaredLicenses shouldBe setOf("License 1", "License 2", "License 3", "License 4")

                    with(processedDeclaredLicense) {
                        spdxExpression shouldBe "License Expression"
                        mappedLicenses shouldBe mapOf(
                            "License 1" to "Mapped License 1",
                            "License 2" to "Mapped License 2",
                        )
                        unmappedLicenses shouldHaveSize 4
                        unmappedLicenses shouldBe setOf("License 1", "License 2", "License 3", "License 4")
                    }
                }
            }

            "limit and sort the result based on query options" {
                val service = PackageService(db)

                val ortRunId = createAnalyzerRunWithPackages(
                    setOf(
                        Package(
                            Identifier("Maven", "com.example", "example", "1.0"),
                            purl = "pkg:maven/com.example/example@1.0",
                            cpe = null,
                            authors = emptySet(),
                            declaredLicenses = emptySet(),
                            ProcessedDeclaredLicense(
                                spdxExpression = "Expression",
                                mappedLicenses = emptyMap(),
                                unmappedLicenses = emptySet()
                            ),
                            description = "An example package",
                            homepageUrl = "https://example.com",
                            binaryArtifact = RemoteArtifact(
                                "https://example.com/example-1.0.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            sourceArtifact = RemoteArtifact(
                                "https://example.com/example-1.0-sources.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            vcs = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            vcsProcessed = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            isMetadataOnly = false,
                            isModified = false
                        ),
                        Package(
                            Identifier("Maven", "com.example", "example2", "1.0"),
                            purl = "pkg:maven/com.example/example2@1.0",
                            cpe = null,
                            authors = emptySet(),
                            declaredLicenses = emptySet(),
                            ProcessedDeclaredLicense(
                                spdxExpression = "Expression",
                                mappedLicenses = emptyMap(),
                                unmappedLicenses = emptySet()
                            ),
                            description = "Another example package",
                            homepageUrl = "https://example.com",
                            binaryArtifact = RemoteArtifact(
                                "https://example.com/example2-1.0.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            sourceArtifact = RemoteArtifact(
                                "https://example.com/example2-1.0-sources.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            vcs = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            vcsProcessed = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            isMetadataOnly = false,
                            isModified = false
                        ),
                        Package(
                            Identifier("Maven", "com.example", "example3", "1.0"),
                            purl = "pkg:maven/com.example/example3@1.0",
                            cpe = null,
                            authors = emptySet(),
                            declaredLicenses = emptySet(),
                            ProcessedDeclaredLicense(
                                spdxExpression = "Expression",
                                mappedLicenses = emptyMap(),
                                unmappedLicenses = emptySet()
                            ),
                            description = "Yet another example package",
                            homepageUrl = "https://example.com",
                            binaryArtifact = RemoteArtifact(
                                "https://example.com/example3-1.0.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            sourceArtifact = RemoteArtifact(
                                "https://example.com/example3-1.0-sources.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            vcs = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            vcsProcessed = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            isMetadataOnly = false,
                            isModified = false
                        )
                    )
                ).id

                val results = service.listForOrtRunId(
                    ortRunId,
                    ListQueryParameters(listOf(OrderField("purl", OrderDirection.DESCENDING)), limit = 2)
                )

                results.data shouldHaveSize 2
                results.totalCount shouldBe 3

                with(results.data.first()) {
                    identifier.name shouldBe "example3"
                }

                with(results.data.last()) {
                    identifier.name shouldBe "example2"
                }
            }

            "return an empty list if no packages were found in an ORT run" {
                val service = PackageService(db)

                val ortRun = createAnalyzerRunWithPackages(emptySet())

                val results = service.listForOrtRunId(ortRun.id).data

                results should beEmpty()
            }
        }

        "countForOrtRunId" should {
            "return count for packages found in an ORT run" {
                val service = PackageService(db)

                val ortRunId = createAnalyzerRunWithPackages(
                    setOf(
                        Package(
                            Identifier("Maven", "com.example", "example", "1.0"),
                            purl = "pkg:maven/com.example/example@1.0",
                            cpe = null,
                            authors = emptySet(),
                            declaredLicenses = emptySet(),
                            ProcessedDeclaredLicense(
                                spdxExpression = null,
                                mappedLicenses = emptyMap(),
                                unmappedLicenses = emptySet()
                            ),
                            description = "An example package",
                            homepageUrl = "https://example.com",
                            binaryArtifact = RemoteArtifact(
                                "https://example.com/example-1.0.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            sourceArtifact = RemoteArtifact(
                                "https://example.com/example-1.0-sources.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            vcs = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            vcsProcessed = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            isMetadataOnly = false,
                            isModified = false
                        ),
                        Package(
                            Identifier("NPM", "com.example", "example2", "1.0"),
                            purl = "pkg:npm/com.example/example2@1.0",
                            cpe = null,
                            authors = emptySet(),
                            declaredLicenses = emptySet(),
                            ProcessedDeclaredLicense(
                                spdxExpression = null,
                                mappedLicenses = emptyMap(),
                                unmappedLicenses = emptySet()
                            ),
                            description = "Another example package",
                            homepageUrl = "https://example.com",
                            binaryArtifact = RemoteArtifact(
                                "https://example.com/example2-1.0.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            sourceArtifact = RemoteArtifact(
                                "https://example.com/example2-1.0-sources.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            vcs = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            vcsProcessed = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            isMetadataOnly = false,
                            isModified = false
                        ),
                    )
                ).id

                service.countForOrtRunId(ortRunId) shouldBe 2
            }
        }

        "countEcosystemsForOrtRun" should {
            "list package types and counts for packages found in an ORT run" {
                val service = PackageService(db)

                val ortRunId = createAnalyzerRunWithPackages(
                    setOf(
                        Package(
                            Identifier("Maven", "com.example", "example", "1.0"),
                            purl = "pkg:maven/com.example/example@1.0",
                            cpe = null,
                            authors = emptySet(),
                            declaredLicenses = emptySet(),
                            ProcessedDeclaredLicense(
                                spdxExpression = "Expression",
                                mappedLicenses = emptyMap(),
                                unmappedLicenses = emptySet()
                            ),
                            description = "An example package",
                            homepageUrl = "https://example.com",
                            binaryArtifact = RemoteArtifact(
                                "https://example.com/example-1.0.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            sourceArtifact = RemoteArtifact(
                                "https://example.com/example-1.0-sources.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            vcs = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            vcsProcessed = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            isMetadataOnly = false,
                            isModified = false
                        ),
                        Package(
                            Identifier("NPM", "com.example", "example2", "1.0"),
                            purl = "pkg:npm/com.example/example2@1.0",
                            cpe = null,
                            authors = emptySet(),
                            declaredLicenses = emptySet(),
                            ProcessedDeclaredLicense(
                                spdxExpression = "Expression",
                                mappedLicenses = emptyMap(),
                                unmappedLicenses = emptySet()
                            ),
                            description = "Another example package",
                            homepageUrl = "https://example.com",
                            binaryArtifact = RemoteArtifact(
                                "https://example.com/example2-1.0.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            sourceArtifact = RemoteArtifact(
                                "https://example.com/example2-1.0-sources.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            vcs = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            vcsProcessed = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            isMetadataOnly = false,
                            isModified = false
                        ),
                        Package(
                            Identifier("Maven", "com.example", "example3", "1.0"),
                            purl = "pkg:maven/com.example/example3@1.0",
                            cpe = null,
                            authors = emptySet(),
                            declaredLicenses = emptySet(),
                            ProcessedDeclaredLicense(
                                spdxExpression = "Expression",
                                mappedLicenses = emptyMap(),
                                unmappedLicenses = emptySet()
                            ),
                            description = "Yet another example package",
                            homepageUrl = "https://example.com",
                            binaryArtifact = RemoteArtifact(
                                "https://example.com/example3-1.0.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            sourceArtifact = RemoteArtifact(
                                "https://example.com/example3-1.0-sources.jar",
                                "sha1:value",
                                "SHA-1"
                            ),
                            vcs = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            vcsProcessed = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            isMetadataOnly = false,
                            isModified = false
                        )
                    )
                ).id

                val ecosystems = service.countEcosystemsForOrtRun(ortRunId)

                ecosystems shouldHaveSize 2
                ecosystems.first().name shouldBe "Maven"
                ecosystems.first().count shouldBe 2
                ecosystems.last().name shouldBe "NPM"
                ecosystems.last().count shouldBe 1
            }
        }
    }

    private fun createAnalyzerRunWithPackages(packages: Set<Package>): OrtRun {
        val repository = fixtures.createRepository()

        val ortRun = fixtures.createOrtRun(
            repositoryId = repository.id,
            revision = "revision",
            jobConfigurations = JobConfigurations()
        )

        val analyzerJob = fixtures.createAnalyzerJob(
            ortRunId = ortRun.id,
            configuration = AnalyzerJobConfiguration(),
        )

        fixtures.analyzerRunRepository.create(
            analyzerJobId = analyzerJob.id,
            startTime = Clock.System.now(),
            endTime = Clock.System.now(),
            environment = Environment(
                ortVersion = "1.0",
                javaVersion = "11.0.16",
                os = "Linux",
                processors = 8,
                maxMemory = 8321499136,
                variables = emptyMap(),
                toolVersions = emptyMap()
            ),
            config = AnalyzerConfiguration(
                allowDynamicVersions = true,
                enabledPackageManagers = emptyList(),
                disabledPackageManagers = emptyList(),
                packageManagers = emptyMap(),
                skipExcluded = true
            ),
            projects = emptySet(),
            packages = packages,
            issues = emptyList(),
            dependencyGraphs = emptyMap()
        )

        return ortRun
    }
}
