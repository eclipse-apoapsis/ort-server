/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun

import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.EnvironmentDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.EnvironmentsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunsIssuesTable
import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphsWrapper

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.selectAll

/**
 * A table to represent an analyzer run.
 */
object AnalyzerRunsTable : LongIdTable("analyzer_runs") {
    val analyzerJobId = reference("analyzer_job_id", AnalyzerJobsTable)
    val environmentId = reference("environment_id", EnvironmentsTable)

    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time")
    val dependencyGraphs = jsonb<DependencyGraphsWrapper>("dependency_graphs")

    /** Get the [AnalyzerRun] for the given [id]. Returns `null` if no run is found. */
    fun getById(id: Long): AnalyzerRun? =
        selectAll().where { AnalyzerRunsTable.id eq id }.singleOrNull()?.let {
            loadAnalyzerRun(it)
        }

    /** Get the [AnalyzerRun] for the given [analyzerJobId]. Returns `null` if no run is found. */
    fun getByAnalyzerJobId(analyzerJobId: Long): AnalyzerRun? =
        selectAll().where { AnalyzerRunsTable.analyzerJobId eq analyzerJobId }.singleOrNull()?.let {
            loadAnalyzerRun(it)
        }

    private fun loadAnalyzerRun(resultRow: ResultRow): AnalyzerRun {
        val analyzerRunId = resultRow[id].value
        val analyzerJobId = resultRow[analyzerJobId].value
        val environment = checkNotNull(EnvironmentsTable.getById(resultRow[environmentId].value))
        val config = checkNotNull(AnalyzerConfigurationsTable.getByAnalyzerRunId(analyzerRunId))
        val projects = ProjectsAnalyzerRunsTable.getProjectsByAnalyzerRunId(analyzerRunId)
        val packages = PackagesAnalyzerRunsTable.getPackagesByAnalyzerRunId(analyzerRunId)
        val ortRunId = checkNotNull(AnalyzerJobsTable.getOrtRunIdById(analyzerJobId))
        val issues = OrtRunsIssuesTable.getIssuesByOrtRunId(ortRunId, AnalyzerRunDao.ISSUE_WORKER_TYPE)

        return AnalyzerRun(
            id = analyzerRunId,
            analyzerJobId = analyzerJobId,
            startTime = resultRow[startTime],
            endTime = resultRow[endTime],
            environment = environment,
            config = config,
            projects = projects,
            packages = packages,
            issues = issues,
            dependencyGraphs = resultRow[dependencyGraphs].dependencyGraphs
        )
    }
}

class AnalyzerRunDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AnalyzerRunDao>(AnalyzerRunsTable) {
        /**
         * Constant for the _worker_ property value set for issues to mark them as created by the Analyzer.
         */
        const val ISSUE_WORKER_TYPE = "analyzer"
    }

    var analyzerJob by AnalyzerJobDao referencedOn AnalyzerRunsTable.analyzerJobId
    var environment by EnvironmentDao referencedOn AnalyzerRunsTable.environmentId

    var startTime by AnalyzerRunsTable.startTime.transformToDatabasePrecision()
    var endTime by AnalyzerRunsTable.endTime.transformToDatabasePrecision()
    var dependencyGraphsWrapper by AnalyzerRunsTable.dependencyGraphs

    val analyzerConfiguration by AnalyzerConfigurationDao backReferencedOn AnalyzerConfigurationsTable.analyzerRunId
    val projects by ProjectDao via ProjectsAnalyzerRunsTable
    var packages by PackageDao via PackagesAnalyzerRunsTable

    fun mapToModel() = AnalyzerRun(
        id = id.value,
        analyzerJobId = analyzerJob.id.value,
        startTime = startTime,
        endTime = endTime,
        environment = environment.mapToModel(),
        config = analyzerConfiguration.mapToModel(),
        projects = projects.mapTo(mutableSetOf(), ProjectDao::mapToModel),
        packages = packages.mapTo(mutableSetOf(), PackageDao::mapToModel),
        issues = analyzerJob.ortRun.issues.filter { it.worker == ISSUE_WORKER_TYPE }.map { it.mapToModel() },
        dependencyGraphs = dependencyGraphsWrapper.dependencyGraphs
    )
}
