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

package org.eclipse.apoapsis.ortserver.dao.queries.analyzer

import org.eclipse.apoapsis.ortserver.dao.Query
import org.eclipse.apoapsis.ortserver.dao.queries.environment.GetEnvironmentQuery
import org.eclipse.apoapsis.ortserver.dao.queries.ortrun.GetIssuesForOrtRunQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll

/** A query to get an [AnalyzerRun] by its ID. Returns `null` if the run is not found. */
class GetAnalyzerRunQuery(
    /** The ID of the analyzer run to retrieve. */
    val analyzerRunId: Long
) : Query<AnalyzerRun?> {
    override fun execute(): AnalyzerRun? =
        AnalyzerRunsTable
            .selectAll()
            .where { AnalyzerRunsTable.id eq analyzerRunId }
            .singleOrNull()?.let {
                loadAnalyzerRun(it)
            }
}

/** A query to get an [AnalyzerRun] for a given [analyzerJobId]. Returns `null` if the run is not found. */
class GetAnalyzerRunForAnalyzerJob(
    /** The ID of the analyzer job to retrieve the run for. */
    val analyzerJobId: Long
) : Query<AnalyzerRun?> {
    override fun execute(): AnalyzerRun? =
        AnalyzerRunsTable
            .selectAll()
            .where { AnalyzerRunsTable.analyzerJobId eq analyzerJobId }
            .singleOrNull()?.let {
                loadAnalyzerRun(it)
            }
}

private fun loadAnalyzerRun(resultRow: ResultRow): AnalyzerRun {
    val analyzerRunId = resultRow[AnalyzerRunsTable.id].value
    val analyzerJobId = resultRow[AnalyzerRunsTable.analyzerJobId].value
    val environment = checkNotNull(GetEnvironmentQuery(resultRow[AnalyzerRunsTable.environmentId].value).execute())
    val config = checkNotNull(GetAnalyzerConfigurationForAnalyzerRunQuery(analyzerRunId).execute())
    val projects = GetProjectsForAnalyzerRunQuery(analyzerRunId).execute()
    val packages = GetPackagesForAnalyzerRunQuery(analyzerRunId).execute()
    val ortRunId = checkNotNull(GetOrtRunIdForAnalyzerJobQuery(analyzerJobId).execute())
    val issues = GetIssuesForOrtRunQuery(ortRunId, AnalyzerRunDao.ISSUE_WORKER_TYPE).execute()

    return AnalyzerRun(
        id = analyzerRunId,
        analyzerJobId = analyzerJobId,
        startTime = resultRow[AnalyzerRunsTable.startTime],
        endTime = resultRow[AnalyzerRunsTable.endTime],
        environment = environment,
        config = config,
        projects = projects,
        packages = packages,
        issues = issues,
        dependencyGraphs = resultRow[AnalyzerRunsTable.dependencyGraphs].dependencyGraphs
    )
}
