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

// TODO: Remove "CONTEXT_RECEIVERS_DEPRECATED" once context parameters become available, see:
// https://kotlinlang.org/docs/whatsnew2020.html#phased-replacement-of-context-receivers-with-context-parameters
@file:Suppress("CONTEXT_RECEIVERS_DEPRECATED", "TooManyFunctions")

package org.eclipse.apoapsis.ortserver.dao.utils

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.minus

import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection

import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.AbstractQuery
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ComparisonOp
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryParameter
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.TextColumnType

/**
 * Transform the given column to an [EntityID] when creating a DAO object. This can be used for foreign key columns to
 * avoid the need to manually create an [EntityID] object.
 */
context(EntityClass<*, *>)
@Suppress("DEPRECATION", "UNCHECKED_CAST") // See https://youtrack.jetbrains.com/issue/EXPOSED-483.
fun <T : EntityID<Long>?> Column<T>.transformToEntityId() =
    transform({ it?.let { EntityID(it, table as IdTable<Long>) } as T }, { it?.value })

/**
 * Transform the given column [to database precision][toDatabasePrecision] when creating a DAO object.
 */
context(EntityClass<*, *>)
@Suppress("UNCHECKED_CAST")
fun <T : Instant?> Column<T>.transformToDatabasePrecision() = transform({ it?.toDatabasePrecision() as T }, { it })

/**
 * Convert an instance to microsecond precision which is stored in the database. This function should always be used
 * when writing timestamps to the database to ensure that the created DAO objects use the same precision as the
 * database.
 */
fun Instant.toDatabasePrecision() = minus(nanosecondsOfSecond, DateTimeUnit.NANOSECOND)

/**
 * Run the provided [query] with the given [parameters] to create a [ListQueryResult]. The entities are mapped to the
 * corresponding model objects using the provided [entityMapper].
 */
internal fun <E : LongEntity, M> SortableEntityClass<E>.listQuery(
    parameters: ListQueryParameters,
    entityMapper: (E) -> M,
    query: SqlExpressionBuilder.() -> Op<Boolean>
): ListQueryResult<M> {
    val filterQuery = find(query)
    val totalCount = filterQuery.count()
    val data = filterQuery.apply(sortableTable, parameters).map(entityMapper)

    return ListQueryResult(data, parameters, totalCount)
}

/**
 * Run the [query] using the [parameters] to create a [ListQueryResult]. The entities are mapped to the  corresponding
 * model objects using the provided [entityMapper].
 */
fun <E : LongEntity, M, T : AbstractQuery<T>> SortableEntityClass<E>.listCustomQuery(
    parameters: ListQueryParameters,
    entityMapper: (ResultRow) -> M,
    query: () -> AbstractQuery<T>
): ListQueryResult<M> {
    val totalCount = query().count()
    val apply = query().apply(sortableTable, parameters)
    val data = apply.map(entityMapper)

    return ListQueryResult(data, parameters, totalCount)
}

/**
 * Run the [query] using the [parameters] and the [customOrders] to create a [ListQueryResult]. The entities are mapped
 * to the corresponding model objects using the provided [entityMapper].
 */
fun <M, T : AbstractQuery<T>> listCustomQueryCustomOrders(
    parameters: ListQueryParameters,
    customOrders: List<Pair<Expression<*>, SortOrder>>,
    entityMapper: (ResultRow) -> M,
    query: () -> AbstractQuery<T>
): ListQueryResult<M> {
    val totalCount = query().count()
    val apply = query().apply(parameters, customOrders)
    val data = apply.map(entityMapper)

    return ListQueryResult(data, parameters, totalCount)
}

/**
 * Apply the given [parameters] to this query result using [table] to resolve the columns to be sorted by.
 */
internal fun <T> SizedIterable<T>.apply(table: SortableTable, parameters: ListQueryParameters): SizedIterable<T> {
    val orders = parameters.sortFields.map {
        val column = table.sortableColumn(it.name)
            ?: throw QueryParametersException("Unsupported field for sorting: '${it.name}'.")
        column to it.direction.toSortOrder()
    }

    val orderedQuery = orderBy(*orders.toTypedArray())
    return parameters.limit?.let { orderedQuery.limit(it).offset(parameters.offset ?: 0) } ?: orderedQuery
}

/**
 * Apply the given [parameters] and [customOrders] to this query result.
 */
internal fun <T> SizedIterable<T>.apply(
    parameters: ListQueryParameters,
    customOrders: List<Pair<Expression<*>, SortOrder>>
): SizedIterable<T> {
    val orderedQuery = orderBy(*customOrders.toTypedArray())
    return parameters.limit?.let { orderedQuery.limit(it).offset(parameters.offset ?: 0) } ?: orderedQuery
}

/**
 * Convert this [OrderDirection] constant to the corresponding [SortOrder].
 */
private fun OrderDirection.toSortOrder(): SortOrder =
    when (this) {
        OrderDirection.ASCENDING -> SortOrder.ASC
        OrderDirection.DESCENDING -> SortOrder.DESC
    }

/**
 * Apply the given [operator] and filter [value] to filter this column by.
 */
fun <T : Comparable<T>> Column<T>.applyFilter(operator: ComparisonOperator, value: T): Op<Boolean> {
    return when (operator) {
        ComparisonOperator.EQUALS -> this eq value
        ComparisonOperator.NOT_EQUALS -> this neq value
        ComparisonOperator.GREATER_THAN -> this greater value
        ComparisonOperator.LESS_THAN -> this less value
        ComparisonOperator.GREATER_OR_EQUAL -> this greaterEq value
        ComparisonOperator.LESS_OR_EQUAL -> this lessEq value
        else -> throw IllegalArgumentException("Unsupported operator for single value")
    }
}

/**
 * Represents a case-insensitive LIKE operation. This is an extension of the [ComparisonOp] class that uses the ILIKE
 * operator, which is the case-insensitive version of the LIKE operator in PostgreSQL.
 */
class InsensitiveLikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "ILIKE")

/**
 * Apply the given [value] to filter this column by using the ILIKE operator.
 */
fun Expression<String>.applyILike(value: String): Op<Boolean> =
    InsensitiveLikeOp(this, QueryParameter("%$value%", TextColumnType()))

/**
 * Apply the given [operator] and filter [values] to filter this column by. This is an overload of the
 * applyFilter function for collections.
 */
fun <T : Comparable<T>> Column<T>.applyFilter(operator: ComparisonOperator, values: Collection<T>): Op<Boolean> {
    return when (operator) {
        ComparisonOperator.IN -> this inList values
        ComparisonOperator.NOT_IN -> this notInList values
        else -> throw IllegalArgumentException("Unsupported operator for collections")
    }
}

/**
 * Apply the given [operator] and filter [values] to filter this column by. This is an overload of the
 * applyFilter function for collections that supports nullable columns.
 */
fun <T : Comparable<T>> Column<T?>.applyFilterNullable(
    operator: ComparisonOperator,
    values: Collection<T>
): Op<Boolean> {
    return when (operator) {
        ComparisonOperator.IN -> this inList values
        ComparisonOperator.NOT_IN -> this notInList values
        else -> throw IllegalArgumentException("Unsupported operator for collections")
    }
}
