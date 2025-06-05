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

package org.eclipse.apoapsis.ortserver.services.ortrun

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.EvaluatorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.EvaluatorRunsRuleViolationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.EvaluatorRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.RuleViolationsTable
import org.eclipse.apoapsis.ortserver.model.CountByCategory
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.OrtRuleViolation
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.services.ResourceNotFoundException

import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.Database

/**
 * A service to interact with rule violations.
 */
class RuleViolationService(private val db: Database, private val ortRunService: OrtRunService) {
    fun listForOrtRunId(
        ortRunId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): ListQueryResult<OrtRuleViolation> {
        val ortRun = ortRunService.getOrtRun(ortRunId) ?: throw ResourceNotFoundException(
            "ORT run with ID $ortRunId not found."
        )

        var comparator = compareBy<OrtRuleViolation> { 0 }

        parameters.sortFields.forEach { orderField ->
            when (orderField.name) {
                "rule" -> {
                    comparator = when (orderField.direction) {
                        OrderDirection.ASCENDING -> comparator.thenBy { it.rule }
                        OrderDirection.DESCENDING -> comparator.thenByDescending { it.rule }
                    }
                }

                "severity" -> {
                    comparator = when (orderField.direction) {
                        OrderDirection.ASCENDING -> comparator.thenBy { it.severity }
                        OrderDirection.DESCENDING -> comparator.thenByDescending { it.severity }
                    }
                }
            }
        }

        val ortResult = ortRunService.generateOrtResult(
            ortRun,
            loadAdvisorRun = false,
            loadScannerRun = false,
            failIfRepoInfoMissing = false
        )

        val ruleViolations = ortResult.getRuleViolations(omitResolved = false).map { it.mapToModel() }

        val sortedResult = ruleViolations.sortedWith(comparator)

        val limitedResults = sortedResult
            .drop(parameters.offset?.toInt() ?: 0)
            .take(parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT)

        return ListQueryResult(
            data = limitedResults,
            params = parameters,
            totalCount = sortedResult.size.toLong()
        )
    }

    /** Count rule violations found in provided ORT runs. */
    suspend fun countForOrtRunIds(vararg ortRunIds: Long): Long = db.dbQuery {
        RuleViolationsTable
            .innerJoin(EvaluatorRunsRuleViolationsTable)
            .innerJoin(EvaluatorRunsTable)
            .innerJoin(EvaluatorJobsTable)
            .select(RuleViolationsTable.id)
            .where { EvaluatorJobsTable.ortRunId inList ortRunIds.asList() }
            .withDistinct()
            .count()
    }

    /** Count rule violations by severity in provided ORT runs. */
    suspend fun countBySeverityForOrtRunIds(vararg ortRunIds: Long): CountByCategory<Severity> = db.dbQuery {
        val countAlias = Count(RuleViolationsTable.id, true)

        val severityToCountMap = Severity.entries.associateWithTo(mutableMapOf()) { 0L }

        RuleViolationsTable
            .innerJoin(EvaluatorRunsRuleViolationsTable)
            .innerJoin(EvaluatorRunsTable)
            .innerJoin(EvaluatorJobsTable)
            .select(RuleViolationsTable.severity, countAlias)
            .where { EvaluatorJobsTable.ortRunId inList ortRunIds.asList() }
            .groupBy(RuleViolationsTable.severity)
            .map { row ->
                severityToCountMap.put(row[RuleViolationsTable.severity], row[countAlias])
            }

        CountByCategory(severityToCountMap)
    }
}
