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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.model.runs.Project
import org.eclipse.apoapsis.ortserver.model.runs.ProjectFilters
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField

import org.jetbrains.exposed.v1.jdbc.Database

class ProjectServiceTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var db: Database
    private lateinit var fixtures: Fixtures
    private lateinit var service: ProjectService

    init {
        beforeEach {
            db = dbExtension.db
            fixtures = dbExtension.fixtures
            service = ProjectService(db)
        }

        "listForOrtRunId" should {
            "map projects for the requested run and retain the default ID order" {
                val projects = linkedSetOf(
                    createProject(Identifier("Maven", "com.example", "first", "1.0")),
                    createProject(Identifier("NPM", "", "second", "2.0"))
                )
                createAnalyzerRunWithProjects(setOf(createProject(Identifier("Gradle", "", "other", "1.0"))))
                val ortRunId = createAnalyzerRunWithProjects(projects).id

                val result = service.listForOrtRunId(ortRunId)

                result.totalCount shouldBe 2
                result.data.map { it.identifier.name }.shouldContainExactly("first", "second")
                with(result.data.first()) {
                    authors shouldContainExactlyInAnyOrder setOf("Author1", "Author2")
                    declaredLicenses shouldBe setOf("Apache-2.0")
                    scopeNames shouldBe setOf("Compile")
                }
            }

            "return an empty result for a run without projects" {
                val ortRunId = createAnalyzerRunWithProjects(emptySet()).id

                val result = service.listForOrtRunId(ortRunId)

                result.data.shouldBeEmpty()
                result.totalCount shouldBe 0
            }

            "filter identifiers by a case-insensitive substring of the full coordinates" {
                val matching = Identifier("Maven", "Com.Example", "matching", "1.0")
                val ortRunId = createAnalyzerRunWithProjects(
                    setOf(
                        createProject(matching),
                        createProject(Identifier("NPM", "com.example", "matching", "1.0")),
                        createProject(Identifier("Maven", "com.example", "other", "1.0"))
                    )
                ).id

                val result = service.listForOrtRunId(
                    ortRunId,
                    filters = ProjectFilters(identifier = ilike("maven:com.example:MATCH"))
                )

                result.data.shouldBeSingleton { it.identifier shouldBe matching }
                result.totalCount shouldBe 1
            }

            "filter definition file paths by a case-insensitive substring" {
                val ortRunId = createAnalyzerRunWithProjects(
                    setOf(
                        createProject(identifier("gradle"), definitionFilePath = "Services/App/BUILD.gradle.kts"),
                        createProject(identifier("maven"), definitionFilePath = "services/app/pom.xml")
                    )
                ).id

                val result = service.listForOrtRunId(
                    ortRunId,
                    filters = ProjectFilters(definitionFilePath = ilike("app/build.GRADLE"))
                )

                result.data.shouldBeSingleton { it.identifier.name shouldBe "gradle" }
            }

            "include projects matching processed or unmapped declared licenses" {
                val licenseUrl = "https://example.com/license"
                val ortRunId = createAnalyzerRunWithProjects(
                    setOf(
                        createProject(identifier("processed"), processedLicense = "MIT"),
                        createProject(
                            identifier("unmapped"),
                            processedLicense = null,
                            unmappedLicenses = setOf(licenseUrl)
                        ),
                        createProject(identifier("other"), processedLicense = "Apache-2.0")
                    )
                ).id

                val result = service.listForOrtRunId(
                    ortRunId,
                    parameters = sortedBy("identifier"),
                    filters = ProjectFilters(declaredLicense = included("MIT", licenseUrl))
                )

                result.data.map { it.identifier.name }.shouldContainExactly("processed", "unmapped")
                result.totalCount shouldBe 2
            }

            "exclude projects matching any selected license and retain projects without licenses" {
                val licenseUrl = "https://example.com/license"
                val ortRunId = createAnalyzerRunWithProjects(
                    setOf(
                        createProject(identifier("processed"), processedLicense = "MIT"),
                        createProject(
                            identifier("unmapped"),
                            processedLicense = "Apache-2.0",
                            unmappedLicenses = setOf(licenseUrl)
                        ),
                        createProject(identifier("none"), processedLicense = null),
                        createProject(identifier("other"), processedLicense = "BSD-2-Clause")
                    )
                ).id

                val result = service.listForOrtRunId(
                    ortRunId,
                    parameters = sortedBy("identifier"),
                    filters = ProjectFilters(declaredLicense = excluded("MIT", licenseUrl))
                )

                result.data.map { it.identifier.name }.shouldContainExactly("none", "other")
                result.totalCount shouldBe 2
            }

            "compose all filters before counting and pagination and scope them to the run" {
                val matching = createProject(
                    Identifier("Maven", "com.example", "matching", "1.0"),
                    definitionFilePath = "modules/app/pom.xml",
                    processedLicense = "MIT"
                )
                val secondMatching = matching.copy(identifier = matching.identifier.copy(version = "2.0"))
                val ortRunId = createAnalyzerRunWithProjects(
                    setOf(
                        matching,
                        secondMatching,
                        matching.copy(
                            identifier = identifier("wrong-license"),
                            processedDeclaredLicense = license("Apache-2.0")
                        ),
                        matching.copy(identifier = identifier("wrong-path"), definitionFilePath = "other/pom.xml")
                    )
                ).id
                createAnalyzerRunWithProjects(
                    setOf(matching.copy(identifier = matching.identifier.copy(version = "3.0")))
                )

                val result = service.listForOrtRunId(
                    ortRunId,
                    parameters = ListQueryParameters(limit = 1, offset = 1),
                    filters = ProjectFilters(
                        identifier = ilike("maven:com.example:matching"),
                        declaredLicense = included("MIT"),
                        definitionFilePath = ilike("modules/app")
                    )
                )

                result.data.shouldBeSingleton { it.identifier.version shouldBe "2.0" }
                result.totalCount shouldBe 2
            }

            "reject unsupported filter operators" {
                val ortRunId = createAnalyzerRunWithProjects(setOf(createProject(identifier("project")))).id

                listOf(
                    ProjectFilters(identifier = FilterOperatorAndValue(ComparisonOperator.EQUALS, "project")),
                    ProjectFilters(declaredLicense = FilterOperatorAndValue(ComparisonOperator.ILIKE, setOf("MIT"))),
                    ProjectFilters(definitionFilePath = FilterOperatorAndValue(ComparisonOperator.EQUALS, "pom.xml"))
                ).forEach { filters ->
                    shouldThrow<IllegalArgumentException> {
                        service.listForOrtRunId(ortRunId, filters = filters)
                    }.message shouldContain "Unsupported operator"
                }
            }

            "sort identifiers in both directions before pagination" {
                val ortRunId = createAnalyzerRunWithProjects(
                    setOf(
                        createProject(Identifier("NPM", "", "z", "1.0")),
                        createProject(Identifier("Maven", "org.example", "b", "1.0")),
                        createProject(Identifier("Maven", "com.example", "a", "2.0")),
                        createProject(Identifier("Maven", "com.example", "a", "1.0"))
                    )
                ).id

                val ascending = service.listForOrtRunId(
                    ortRunId,
                    ListQueryParameters(
                        sortFields = listOf(OrderField("identifier", OrderDirection.ASCENDING)),
                        limit = 2
                    )
                )
                val descending = service.listForOrtRunId(ortRunId, sortedBy("identifier", OrderDirection.DESCENDING))

                ascending.data.map { it.identifier.version }.shouldContainExactly("1.0", "2.0")
                descending.data.map { it.identifier.type }.shouldContainExactly("NPM", "Maven", "Maven", "Maven")
            }

            "sort declared licenses and definition file paths in both directions" {
                val ortRunId = createAnalyzerRunWithProjects(
                    setOf(
                        createProject(identifier("mit"), "z/pom.xml", "MIT"),
                        createProject(identifier("apache"), "a/pom.xml", "Apache-2.0"),
                        createProject(identifier("bsd"), "m/pom.xml", "BSD-2-Clause")
                    )
                ).id

                service.listForOrtRunId(ortRunId, sortedBy("declaredLicense"))
                    .data.map { it.identifier.name }.shouldContainExactly("apache", "bsd", "mit")
                service.listForOrtRunId(ortRunId, sortedBy("declaredLicense", OrderDirection.DESCENDING))
                    .data.map { it.identifier.name }.shouldContainExactly("mit", "bsd", "apache")
                service.listForOrtRunId(ortRunId, sortedBy("definitionFilePath"))
                    .data.map { it.identifier.name }.shouldContainExactly("apache", "bsd", "mit")
                service.listForOrtRunId(ortRunId, sortedBy("definitionFilePath", OrderDirection.DESCENDING))
                    .data.map { it.identifier.name }.shouldContainExactly("mit", "bsd", "apache")
            }

            "use multiple sort fields in priority order and project ID as the final tie-breaker" {
                val ortRunId = createAnalyzerRunWithProjects(
                    linkedSetOf(
                        createProject(identifier("first"), "same/pom.xml", null),
                        createProject(identifier("second"), "same/pom.xml", null),
                        createProject(identifier("third"), "other/pom.xml", "MIT"),
                        createProject(identifier("fourth"), "same/pom.xml", "MIT")
                    )
                ).id
                val parameters = ListQueryParameters(
                    sortFields = listOf(
                        OrderField("declaredLicense", OrderDirection.ASCENDING),
                        OrderField("definitionFilePath", OrderDirection.ASCENDING)
                    )
                )

                val result = service.listForOrtRunId(ortRunId, parameters)

                result.data.map { it.identifier.name }
                    .shouldContainExactly("third", "fourth", "first", "second")
            }

            "reject unsupported sort fields" {
                val ortRunId = createAnalyzerRunWithProjects(setOf(createProject(identifier("project")))).id

                shouldThrow<QueryParametersException> {
                    service.listForOrtRunId(ortRunId, sortedBy("unknown"))
                }.message shouldContain "Unsupported field for sorting"
            }
        }

        "getProcessedDeclaredLicenses" should {
            "return distinct sorted expressions scoped to the requested run" {
                val ortRunId = createAnalyzerRunWithProjects(
                    setOf(
                        createProject(identifier("mit-1"), processedLicense = "MIT"),
                        createProject(identifier("mit-2"), processedLicense = "MIT"),
                        createProject(identifier("apache"), processedLicense = "Apache-2.0"),
                        createProject(identifier("bsd"), processedLicense = "bsd-2-Clause"),
                        createProject(identifier("none"), processedLicense = null)
                    )
                ).id
                createAnalyzerRunWithProjects(
                    setOf(createProject(identifier("other-run"), processedLicense = "EPL-2.0"))
                )

                service.getProcessedDeclaredLicenses(ortRunId)
                    .shouldContainExactly("Apache-2.0", "bsd-2-Clause", "MIT")
            }

            "return an empty list if the run has no processed expressions" {
                val ortRunId = createAnalyzerRunWithProjects(
                    setOf(createProject(identifier("none"), processedLicense = null))
                ).id

                service.getProcessedDeclaredLicenses(ortRunId).shouldBeEmpty()
            }
        }

        "getUnmappedDeclaredLicenses" should {
            "return distinct sorted strings scoped to the requested run" {
                val ortRunId = createAnalyzerRunWithProjects(
                    setOf(
                        createProject(
                            identifier("first"),
                            unmappedLicenses = setOf("unknown-license", "custom-license")
                        ),
                        createProject(
                            identifier("second"),
                            unmappedLicenses = setOf("custom-license", "another-unknown-license")
                        )
                    )
                ).id
                createAnalyzerRunWithProjects(
                    setOf(
                        createProject(
                            identifier("other-run"),
                            unmappedLicenses = setOf("excluded-unknown-license")
                        )
                    )
                )

                service.getUnmappedDeclaredLicenses(ortRunId)
                    .shouldContainExactly("another-unknown-license", "custom-license", "unknown-license")
            }

            "return an empty list if the run has no unmapped licenses" {
                val ortRunId = createAnalyzerRunWithProjects(
                    setOf(createProject(identifier("none"), processedLicense = null))
                ).id

                service.getUnmappedDeclaredLicenses(ortRunId).shouldBeEmpty()
            }
        }
    }

    private fun identifier(name: String) = Identifier("Maven", "com.example", name, "1.0")

    private fun ilike(value: String) = FilterOperatorAndValue(ComparisonOperator.ILIKE, value)

    private fun included(vararg values: String) = FilterOperatorAndValue(ComparisonOperator.IN, values.toSet())

    private fun excluded(vararg values: String) = FilterOperatorAndValue(ComparisonOperator.NOT_IN, values.toSet())

    private fun sortedBy(
        field: String,
        direction: OrderDirection = OrderDirection.ASCENDING
    ) = ListQueryParameters(sortFields = listOf(OrderField(field, direction)))

    private fun license(spdxExpression: String?, unmappedLicenses: Set<String> = emptySet()) =
        ProcessedDeclaredLicense(spdxExpression, emptyMap(), unmappedLicenses)

    private fun createProject(
        identifier: Identifier,
        definitionFilePath: String = "pom.xml",
        processedLicense: String? = "Apache-2.0",
        unmappedLicenses: Set<String> = emptySet()
    ) = Project(
        identifier = identifier,
        definitionFilePath = definitionFilePath,
        authors = setOf("Author1", "Author2"),
        declaredLicenses = processedLicense?.let(::setOf).orEmpty() + unmappedLicenses,
        processedDeclaredLicense = license(processedLicense, unmappedLicenses),
        vcs = VcsInfo(RepositoryType.GIT, "https://example.com", "main", "v1.0.0"),
        vcsProcessed = VcsInfo(RepositoryType.GIT, "https://example.com", "main", "v1.0.0"),
        description = "Description",
        homepageUrl = "https://example.com",
        scopeNames = setOf("Compile")
    )

    private fun createAnalyzerRunWithProjects(projects: Set<Project>): OrtRun {
        val ortRun = fixtures.createOrtRun()
        val analyzerJob = fixtures.createAnalyzerJob(ortRun.id)

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
                variables = emptyMap()
            ),
            config = AnalyzerConfiguration(
                allowDynamicVersions = true,
                enabledPackageManagers = emptyList(),
                disabledPackageManagers = emptyList(),
                packageManagers = emptyMap(),
                skipExcluded = true
            ),
            projects = projects,
            packages = emptySet(),
            issues = emptyList(),
            dependencyGraphs = emptyMap()
        )

        return ortRun
    }
}
