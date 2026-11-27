/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun

import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.runs.advisor.HealthSummary

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select

/**
 * A table to store the summary of the health of a package as a whole, as reported by one advisor. The overall score of
 * a summary is stored in [HealthMetricsTable].
 */
object HealthSummariesTable : LongIdTable("health_summaries") {
    val advisorResultId = reference("advisor_result_id", AdvisorResultsTable)

    val analyzedAt = timestamp("analyzed_at").nullable()
    val analyzedRevision = text("analyzed_revision").nullable()
    val toolVersion = text("tool_version").nullable()

    /** Store the given [summary] of the advisor result with the given [advisorResultId] and return its ID. */
    fun insert(summary: HealthSummary, advisorResultId: EntityID<Long>): EntityID<Long> =
        insertAndGetId {
            it[this.advisorResultId] = advisorResultId
            it[analyzedAt] = summary.analyzedAt?.toDatabasePrecision()
            it[analyzedRevision] = summary.analyzedRevision
            it[toolVersion] = summary.toolVersion
        }

    /**
     * Return the summaries of all advisor results of the advisor run with the given [advisorRunId], together with their
     * overall scores, by the ID of the advisor result.
     */
    fun getByAdvisorRunId(advisorRunId: Long): Map<Long, HealthSummary> =
        join(AdvisorResultsTable, JoinType.INNER, advisorResultId, AdvisorResultsTable.id)
            .join(
                AdvisorRunsIdentifiersTable,
                JoinType.INNER,
                AdvisorResultsTable.advisorRunIdentifierId,
                AdvisorRunsIdentifiersTable.id
            )
            // A summary does not need to have an overall score.
            .join(HealthMetricsTable, JoinType.LEFT, id, HealthMetricsTable.healthSummaryId)
            .join(
                HealthMetricDefinitionsTable,
                JoinType.LEFT,
                HealthMetricsTable.healthMetricDefinitionId,
                HealthMetricDefinitionsTable.id
            )
            .select(columns + HealthMetricsTable.columns + HealthMetricDefinitionsTable.columns)
            .where { AdvisorRunsIdentifiersTable.advisorRunId eq advisorRunId }
            .associate { row ->
                row[advisorResultId].value to HealthSummary(
                    overallScore = row.getOrNull(HealthMetricsTable.id)?.let { row.toHealthMetric() },
                    analyzedAt = row[analyzedAt],
                    analyzedRevision = row[analyzedRevision],
                    toolVersion = row[toolVersion]
                )
            }
}
