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

package org.eclipse.apoapsis.ortserver.services.ortrun

import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.dao.utils.applyILike
import org.eclipse.apoapsis.ortserver.model.VulnerabilityFilters
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.services.utils.toSortOrder
import org.eclipse.apoapsis.ortserver.shared.apimodel.Identifier

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.concat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Return a [Query] for the projected vulnerabilities of the ORT run with the given [ortRunId], filtered by the given
 * [filters], and the total number of matching rows. The query is sorted and paged according to the given
 * [parameters].
 */
internal fun queryProjectedRunVulnerabilities(
    ortRunId: Long,
    parameters: ListQueryParameters,
    filters: VulnerabilityFilters
): Pair<Query, Long> {
    val table = ProjectedRunVulnerabilitiesTable
    val query = table.selectAll().where { (table.ortRunId eq ortRunId) and filters.toCondition() }
    val totalCount = query.count()

    val sortFields = parameters.sortFields.ifEmpty { listOf(OrderField("externalId", OrderDirection.ASCENDING)) }

    sortFields.forEach { orderField ->
        val sortOrder = orderField.direction.toSortOrder()

        when (orderField.name) {
            "rating" -> query.orderBy(table.rating to sortOrder)

            "externalId" -> query.orderBy(table.externalId to sortOrder)

            "identifier" -> query.orderBy(
                table.identifierType to sortOrder,
                table.identifierNamespace to sortOrder,
                table.identifierName to sortOrder,
                table.identifierVersion to sortOrder
            )

            "purl" -> query.orderBy(table.purl to sortOrder)

            else -> throw QueryParametersException("Unsupported field for sorting: '${orderField.name}'.")
        }
    }

    query.orderBy(
        table.vulnerabilityId to SortOrder.ASC,
        table.identifierId to SortOrder.ASC,
        table.advisorName to SortOrder.ASC
    )

    query.limit(parameters.limit ?: ListQueryParameters.DEFAULT_LIMIT).offset(parameters.offset ?: 0L)

    return query to totalCount
}

private fun VulnerabilityFilters.toCondition(): Op<Boolean> {
    val table = ProjectedRunVulnerabilitiesTable
    var condition: Op<Boolean> = Op.TRUE

    resolved?.also { condition = condition and (table.resolved eq it) }

    fixAvailable?.also { condition = condition and (table.fixAvailable eq it) }

    rating?.also {
        val ratings = it.value.map { rating -> rating.ordinal }
        condition = condition and when (it.operator) {
            ComparisonOperator.IN -> table.rating inList ratings
            else -> table.rating notInList ratings
        }
    }

    identifier?.also {
        val identifier = concat(
            table.identifierType,
            stringLiteral(":"),
            table.identifierNamespace,
            stringLiteral(":"),
            table.identifierName,
            stringLiteral(":"),
            table.identifierVersion
        )

        condition = condition and identifier.applyILike(it.value)
    }

    purl?.also { condition = condition and table.purl.applyILike(it.value) }

    externalId?.also { condition = condition and table.externalId.applyILike(it.value) }

    advisors?.also {
        // Plugin IDs are case-insensitive, so the advisor names are compared ignoring case.
        val advisorNames = it.value.map { name -> name.lowercase() }
        condition = condition and when (it.operator) {
            ComparisonOperator.IN -> table.advisorName.lowerCase() inList advisorNames
            else -> table.advisorName.lowerCase() notInList advisorNames
        }
    }

    return condition
}

/** Map a row of the [ProjectedRunVulnerabilitiesTable] to an [Identifier]. */
internal fun ResultRow.toProjectedIdentifier() = Identifier(
    type = this[ProjectedRunVulnerabilitiesTable.identifierType],
    namespace = this[ProjectedRunVulnerabilitiesTable.identifierNamespace],
    name = this[ProjectedRunVulnerabilitiesTable.identifierName],
    version = this[ProjectedRunVulnerabilitiesTable.identifierVersion]
)
