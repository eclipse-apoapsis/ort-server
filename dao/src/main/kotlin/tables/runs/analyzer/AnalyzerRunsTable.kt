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

package org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer

import org.eclipse.apoapsis.ortserver.dao.tables.AnalyzerJobDao
import org.eclipse.apoapsis.ortserver.dao.tables.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.EnvironmentDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.EnvironmentsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.IdentifierOrtIssueDao
import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphsWrapper

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * A table to represent an analyzer run.
 */
object AnalyzerRunsTable : LongIdTable("analyzer_runs") {
    val analyzerJobId = reference("analyzer_job_id", AnalyzerJobsTable)
    val environmentId = reference("environment_id", EnvironmentsTable)

    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time")
    val dependencyGraphs = jsonb<DependencyGraphsWrapper>("dependency_graphs")
}

class AnalyzerRunDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AnalyzerRunDao>(AnalyzerRunsTable)

    var analyzerJob by AnalyzerJobDao referencedOn AnalyzerRunsTable.analyzerJobId
    var environment by EnvironmentDao referencedOn AnalyzerRunsTable.environmentId

    var startTime by AnalyzerRunsTable.startTime.transform({ it.toDatabasePrecision() }, { it })
    var endTime by AnalyzerRunsTable.endTime.transform({ it.toDatabasePrecision() }, { it })
    var dependencyGraphsWrapper by AnalyzerRunsTable.dependencyGraphs

    val analyzerConfiguration by AnalyzerConfigurationDao backReferencedOn AnalyzerConfigurationsTable.analyzerRunId
    val projects by ProjectDao referrersOn ProjectsTable.analyzerRunId
    var packages by PackageDao via PackagesAnalyzerRunsTable
    var issues by IdentifierOrtIssueDao via AnalyzerRunsIdentifiersOrtIssuesTable

    fun mapToModel() = AnalyzerRun(
        id = id.value,
        analyzerJobId = analyzerJob.id.value,
        startTime = startTime,
        endTime = endTime,
        environment = environment.mapToModel(),
        config = analyzerConfiguration.mapToModel(),
        projects = projects.mapTo(mutableSetOf(), ProjectDao::mapToModel),
        packages = packages.mapTo(mutableSetOf(), PackageDao::mapToModel),
        issues = issues.groupBy { it.identifier }.map { (identifier, idToIssues) ->
            identifier.mapToModel() to
                    idToIssues.filter { it.identifier == identifier }.map { it.ortIssueDao.mapToModel() }
        }.toMap(),
        dependencyGraphs = dependencyGraphsWrapper.dependencyGraphs
    )
}
