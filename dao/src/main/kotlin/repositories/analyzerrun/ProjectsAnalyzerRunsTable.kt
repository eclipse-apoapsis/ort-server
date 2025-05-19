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

package org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun

import org.eclipse.apoapsis.ortserver.model.runs.Project

import org.jetbrains.exposed.sql.Table

/**
 * An intermediate table to store references from [ProjectsTable] and [AnalyzerRunsTable].
 */
object ProjectsAnalyzerRunsTable : Table("projects_analyzer_runs") {
    val projectId = reference("project_id", ProjectsTable)
    val analyzerRunId = reference("analyzer_run_id", AnalyzerRunsTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(projectId, analyzerRunId, name = "${tableName}_pkey")

    /** Get the [Project]s for the given [analyzerRunId]. */
    fun getProjectsByAnalyzerRunId(analyzerRunId: Long): Set<Project> {
        val projectIds = select(projectId)
            .where { ProjectsAnalyzerRunsTable.analyzerRunId eq analyzerRunId }
            .mapTo(mutableSetOf()) { it[projectId].value }

        return ProjectsTable.getByIds(projectIds)
    }
}
