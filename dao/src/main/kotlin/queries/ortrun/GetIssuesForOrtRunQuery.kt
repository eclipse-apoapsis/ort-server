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

package org.eclipse.apoapsis.ortserver.dao.queries.ortrun

import org.eclipse.apoapsis.ortserver.dao.Query
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunsIssuesTable
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue

import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll

/**
 * A query to get the [Issue]s for a given [ortRunId] and [issueWorkerType]. Returns an empty list if no issues are
 * found.
 */
class GetIssuesForOrtRunQuery(
    /** The ID of the ORT run to retrieve issues for. */
    val ortRunId: Long,

    /** The worker to retrieve issues for. */
    val issueWorkerType: String
) : Query<List<Issue>> {
    override fun execute(): List<Issue> =
        OrtRunsIssuesTable
            .innerJoin(IssuesTable)
            .leftJoin(IdentifiersTable)
            .selectAll()
            .where { OrtRunsIssuesTable.ortRunId eq ortRunId }
            .andWhere { OrtRunsIssuesTable.worker eq issueWorkerType }
            .mapNotNull {
                val identifier = it[OrtRunsIssuesTable.identifierId]?.let { id ->
                    Identifier(
                        type = it[IdentifiersTable.type],
                        namespace = it[IdentifiersTable.namespace],
                        name = it[IdentifiersTable.name],
                        version = it[IdentifiersTable.version]
                    )
                }

                Issue(
                    timestamp = it[OrtRunsIssuesTable.timestamp],
                    source = it[IssuesTable.issueSource],
                    message = it[IssuesTable.message],
                    severity = it[IssuesTable.severity],
                    affectedPath = it[IssuesTable.affectedPath],
                    identifier = identifier,
                    worker = it[OrtRunsIssuesTable.worker]
                )
            }
}
