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

package org.eclipse.apoapsis.ortserver.dao.migrations

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.should

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.disableForeignKeyConstraints
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseMigrationTestExtension
import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphsWrapper

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.xTimestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Suppress("ClassNaming")
class V71__projectsAnalyzerRunsTest : WordSpec() {
    val extension = extension(DatabaseMigrationTestExtension("70", "71"))

    init {
        "the migration" should {
            "keep associations between analyzer runs and projects" {
                /**
                 * Create five analyzer runs each associated to 5 projects.
                 */
                fun createRunAndProjects(): Map<Long, List<Long>> =
                    (1..5).map { V69AnalyzerRunsTable.create() }.associateWith { runId ->
                        (1..5).map { V69ProjectsTable.create(runId) }
                    }

                val expectedRunsAndProjects = transaction {
                    disableForeignKeyConstraints {
                        createRunAndProjects()
                    }
                }

                extension.testAppliedMigration {
                    transaction {
                        val actualRunsAndProjects = V70ProjectsAnalyzerRunsTable.selectAll().map {
                            it[V70ProjectsAnalyzerRunsTable.analyzerRunId].value to
                                    it[V70ProjectsAnalyzerRunsTable.projectId].value
                        }.groupBy { it.first }.mapValues { (_, value) -> value.map { it.second } }

                        actualRunsAndProjects shouldContainExactly expectedRunsAndProjects
                    }
                }
            }

            "add missing processed declared licenses" {
                var project1: Long = 0
                var project2: Long = 0
                var project3: Long = 0

                var package1: Long = 0
                var package2: Long = 0
                var package3: Long = 0

                transaction {
                    disableForeignKeyConstraints {
                        project1 = V69ProjectsTable.create(V69AnalyzerRunsTable.create())
                        project2 = V69ProjectsTable.create(V69AnalyzerRunsTable.create())
                        project3 = V69ProjectsTable.create(V69AnalyzerRunsTable.create())

                        package1 = V69PackagesTable.create(V69AnalyzerRunsTable.create())
                        package2 = V69PackagesTable.create(V69AnalyzerRunsTable.create())
                        package3 = V69PackagesTable.create(V69AnalyzerRunsTable.create())

                        V70ProcessedDeclaredLicensesTable.create(projectId = project2, spdxExpression = "Apache-2.0")
                        V70ProcessedDeclaredLicensesTable.create(packageId = package2, spdxExpression = "Apache-2.0")
                    }
                }

                extension.testAppliedMigration {
                    transaction {
                        V70ProcessedDeclaredLicensesTable.findLicensesForProject(project1) should
                                containExactly("NOASSERTION")
                        V70ProcessedDeclaredLicensesTable.findLicensesForProject(project2) should
                                containExactly("Apache-2.0")
                        V70ProcessedDeclaredLicensesTable.findLicensesForProject(project3) should
                                containExactly("NOASSERTION")

                        V70ProcessedDeclaredLicensesTable.findLicensesForPackage(package1) should
                                containExactly("NOASSERTION")
                        V70ProcessedDeclaredLicensesTable.findLicensesForPackage(package2) should
                                containExactly("Apache-2.0")
                        V70ProcessedDeclaredLicensesTable.findLicensesForPackage(package3) should
                                containExactly("NOASSERTION")
                    }
                }
            }
        }
    }
}

private object V69AnalyzerRunsTable : LongIdTable("analyzer_runs") {
    val analyzerJobId = long("analyzer_job_id")
    val environmentId = long("environment_id")

    val startTime = xTimestamp("start_time")
    val endTime = xTimestamp("end_time")
    val dependencyGraphs = jsonb<DependencyGraphsWrapper>("dependency_graphs")

    fun create() = insertAndGetId {
        it[analyzerJobId] = 1
        it[environmentId] = 1
        it[startTime] = Clock.System.now()
        it[endTime] = Clock.System.now()
        it[dependencyGraphs] = DependencyGraphsWrapper(emptyMap())
    }.value
}

private object V69ProjectsTable : LongIdTable("projects") {
    val analyzerRunId = reference("analyzer_run_id", V69AnalyzerRunsTable)
    val identifierId = long("identifier_id")
    val vcsId = long("vcs_id")
    val vcsProcessedId = long("vcs_processed_id")

    val definitionFilePath = text("definition_file_path")
    val homepageUrl = text("homepage_url")

    fun create(analyzerRunId: Long) = insertAndGetId {
        it[this.analyzerRunId] = analyzerRunId
        it[identifierId] = 1
        it[vcsId] = 1
        it[vcsProcessedId] = 1
        it[definitionFilePath] = "definitionFilePath"
        it[homepageUrl] = "homepageUrl"
    }.value
}

private object V69PackagesTable : LongIdTable("packages") {
    val identifierId = long("identifier_id")
    val vcsId = long("vcs_id")
    val vcsProcessedId = long("vcs_processed_id")
    val binaryArtifactId = long("binary_artifact_id")
    val sourceArtifactId = long("source_artifact_id")

    val purl = text("purl")
    val description = text("description")
    val homepageUrl = text("homepage_url")

    fun create(analyzerRunId: Long) = insertAndGetId {
        it[identifierId] = 1
        it[vcsId] = 1
        it[vcsProcessedId] = 1
        it[binaryArtifactId] = 1
        it[sourceArtifactId] = 1
        it[purl] = "purl"
        it[description] = "description"
        it[homepageUrl] = "homepageUrl"
    }.value.also { packageId ->
        V69PackagesAnalyzerRunsTable.insert {
            it[V69PackagesAnalyzerRunsTable.packageId] = packageId
            it[V69PackagesAnalyzerRunsTable.analyzerRunId] = analyzerRunId
        }
    }
}

private object V69PackagesAnalyzerRunsTable : Table("packages_analyzer_runs") {
    val packageId = reference("package_id", V69PackagesTable)
    val analyzerRunId = reference("analyzer_run_id", V69AnalyzerRunsTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(packageId, analyzerRunId, name = "${tableName}_pkey")
}

private object V70ProjectsAnalyzerRunsTable : Table("projects_analyzer_runs") {
    val projectId = reference("project_id", V69ProjectsTable)
    val analyzerRunId = reference("analyzer_run_id", V69AnalyzerRunsTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(projectId, analyzerRunId, name = "${tableName}_pkey")
}

private object V70ProcessedDeclaredLicensesTable : LongIdTable("processed_declared_licenses") {
    val packageId = reference("package_id", V69PackagesTable).nullable()
    val projectId = reference("project_id", V69ProjectsTable).nullable()

    val spdxExpression = text("spdx_expression").nullable()

    fun create(packageId: Long? = null, projectId: Long? = null, spdxExpression: String? = null) = insertAndGetId {
        it[this.packageId] = packageId
        it[this.projectId] = projectId
        it[this.spdxExpression] = spdxExpression
    }.value

    fun findLicensesForPackage(packageId: Long) =
        selectAll().where { V70ProcessedDeclaredLicensesTable.packageId eq packageId }.map { it[spdxExpression] }

    fun findLicensesForProject(projectId: Long) =
        selectAll().where { V70ProcessedDeclaredLicensesTable.projectId eq projectId }.map { it[spdxExpression] }
}
