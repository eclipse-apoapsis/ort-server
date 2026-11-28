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

import org.eclipse.apoapsis.ortserver.dao.utils.enumerationByName
import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Criticality
import org.eclipse.apoapsis.ortserver.model.runs.advisor.HealthMetric

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select

/**
 * A table to store the values of the project health metrics that advisors report. A row belongs either to an advisor
 * result, as one of its metrics, or to a health summary, as its overall score.
 */
object HealthMetricsTable : LongIdTable("health_metrics") {
    val advisorResultId = reference("advisor_result_id", AdvisorResultsTable).nullable()
    val healthSummaryId = reference("health_summary_id", HealthSummariesTable).nullable()
    val healthMetricDefinitionId = reference("health_metric_definition_id", HealthMetricDefinitionsTable)

    val value = double("value").nullable()
    val referenceValue = double("reference_value").nullable()
    val criticality = enumerationByName<Criticality>("criticality").nullable()
    val reason = text("reason").nullable()
    val details = jsonb<List<String>>("details").default(emptyList())

    /**
     * Store the given [metric] with the given [definitionId]. Exactly one of [advisorResultId] and [healthSummaryId]
     * must be set, to say whether the metric belongs to an advisor result or is the overall score of a summary.
     */
    fun insert(
        metric: HealthMetric,
        definitionId: EntityID<Long>,
        advisorResultId: EntityID<Long>? = null,
        healthSummaryId: EntityID<Long>? = null
    ) {
        insert {
            it[this.advisorResultId] = advisorResultId
            it[this.healthSummaryId] = healthSummaryId
            it[healthMetricDefinitionId] = definitionId
            it[value] = metric.value
            it[referenceValue] = metric.referenceValue
            it[criticality] = metric.criticality
            it[reason] = metric.reason
            it[details] = metric.details
        }
    }

    /**
     * Return the metrics of all advisor results of the advisor run with the given [advisorRunId], grouped by the ID of
     * the advisor result. Each list keeps the order in which the advisor reported the metrics. Overall scores are not
     * included.
     */
    fun getByAdvisorRunId(advisorRunId: Long): Map<Long, List<HealthMetric>> =
        join(HealthMetricDefinitionsTable, JoinType.INNER, healthMetricDefinitionId, HealthMetricDefinitionsTable.id)
            .join(AdvisorResultsTable, JoinType.INNER, advisorResultId, AdvisorResultsTable.id)
            .join(
                AdvisorRunsIdentifiersTable,
                JoinType.INNER,
                AdvisorResultsTable.advisorRunIdentifierId,
                AdvisorRunsIdentifiersTable.id
            )
            .select(columns + HealthMetricDefinitionsTable.columns)
            .where { AdvisorRunsIdentifiersTable.advisorRunId eq advisorRunId }
            .orderBy(id)
            .map { row -> checkNotNull(row[advisorResultId]).value to row.toHealthMetric() }
            .groupBy({ it.first }, { it.second })
}

/** Create a [HealthMetric] from a row that contains the columns of [HealthMetricsTable] and its definition. */
internal fun ResultRow.toHealthMetric() = HealthMetric(
    name = this[HealthMetricDefinitionsTable.name],
    documentation = this[HealthMetricDefinitionsTable.documentation],
    documentationLink = this[HealthMetricDefinitionsTable.documentationLink],
    minValue = this[HealthMetricDefinitionsTable.minValue],
    maxValue = this[HealthMetricDefinitionsTable.maxValue],
    higherIsBetter = this[HealthMetricDefinitionsTable.higherIsBetter],
    value = this[HealthMetricsTable.value],
    referenceValue = this[HealthMetricsTable.referenceValue],
    criticality = this[HealthMetricsTable.criticality],
    reason = this[HealthMetricsTable.reason],
    details = this[HealthMetricsTable.details]
)
