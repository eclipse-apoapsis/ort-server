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

package org.eclipse.apoapsis.ortserver.services

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.model.runs.Project
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo

import org.jetbrains.exposed.sql.Database

class ProjectServiceTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var db: Database
    private lateinit var fixtures: Fixtures

    init {
        beforeEach {
            db = dbExtension.db
            fixtures = dbExtension.fixtures
        }

        "listForOrtRunId" should {
            "return projects for the given ORT run ID, ignoring projects from other runs" {
                val service = ProjectService(db)

                val projects1 = createProjects(
                    listOf(
                        Identifier("Maven", "com.example1", "project1", "1.1.5"),
                        Identifier("Maven", "com.example2", "project2", "2.0.1"),
                        Identifier("Maven", "com.example3", "project3", "3.5.2")
                    )
                )

                val projects2 = createProjects(
                    listOf(
                        Identifier("Maven", "com.example4", "project4", "4.0.5")
                    )
                )
                createAnalyzerRunWithProjects(projects2)

                val ortRunId = createAnalyzerRunWithProjects(projects1).id

                val results = service.listForOrtRunId(ortRunId).data

                results shouldHaveSize projects1.size
                results.map { it.identifier } shouldContainExactlyInAnyOrder projects1.map { it.identifier }

                with(results.first()) {
                    authors shouldContainExactlyInAnyOrder setOf("Author1", "Author2")
                    declaredLicenses shouldBe setOf("Apache-2.0")
                    identifier.type shouldBe "Maven"
                    identifier.namespace shouldBe "com.example1"
                    identifier.name shouldBe "project1"
                    identifier.version shouldBe "1.1.5"
                }
            }
        }
    }

    private fun createProjects(identifiers: List<Identifier>): Set<Project> =
        identifiers.map { identifier ->
            Project(
                identifier = identifier,
                definitionFilePath = "pom.xml",
                authors = setOf("Author1", "Author2"),
                declaredLicenses = setOf("Apache-2.0"),
                processedDeclaredLicense = ProcessedDeclaredLicense("Apache-2.0", emptyMap(), emptySet()),
                vcs = VcsInfo(RepositoryType.GIT, "https://example.com", "main", "v1.0.0"),
                vcsProcessed = VcsInfo(RepositoryType.GIT, "https://example.com", "main", "v1.0.0"),
                description = "Description",
                homepageUrl = "https://example.com",
                scopeNames = setOf("Compile")
            )
        }.toSet()

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
            projects = projects,
            packages = emptySet(),
            issues = emptyList(),
            dependencyGraphs = emptyMap()
        )

        return ortRun
    }
}
