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

import org.eclipse.apoapsis.ortserver.dao.utils.Sha256TextFunction
import org.eclipse.apoapsis.ortserver.model.runs.advisor.HealthMetric

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.jdbc.insertIgnore

/**
 * A table to store the definitions of project health metrics. A definition describes how to read the values of a
 * metric and is shared by all advisor results that report the metric.
 */
object HealthMetricDefinitionsTable : LongIdTable("health_metric_definitions") {
    val name = text("name")
    val documentation = text("documentation").nullable()
    val documentationLink = text("documentation_link").nullable()
    val minValue = double("min_value").nullable()
    val maxValue = double("max_value").nullable()
    val higherIsBetter = bool("higher_is_better").nullable()
}

class HealthMetricDefinitionDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<HealthMetricDefinitionDao>(HealthMetricDefinitionsTable) {
        /**
         * Find the definition of the given [metric]. The texts are compared by their hash, like the unique index on
         * the table does, so that the lookup can use the index.
         */
        private fun findByMetric(metric: HealthMetric): HealthMetricDefinitionDao? = find {
            (HealthMetricDefinitionsTable.name eq metric.name) and
                    sha256Eq(HealthMetricDefinitionsTable.documentation, metric.documentation) and
                    sha256Eq(HealthMetricDefinitionsTable.documentationLink, metric.documentationLink) and
                    (HealthMetricDefinitionsTable.minValue eq metric.minValue) and
                    (HealthMetricDefinitionsTable.maxValue eq metric.maxValue) and
                    (HealthMetricDefinitionsTable.higherIsBetter eq metric.higherIsBetter)
        }.firstOrNull()

        /**
         * Return the definition of the given [metric], creating it if it does not exist yet. The definition is
         * inserted with `ON CONFLICT DO NOTHING`, so that concurrent transactions creating the same definition do not
         * fail with a unique constraint violation.
         */
        fun getOrPut(metric: HealthMetric): HealthMetricDefinitionDao =
            findByMetric(metric) ?: run {
                HealthMetricDefinitionsTable.insertIgnore {
                    it[name] = metric.name
                    it[documentation] = metric.documentation
                    it[documentationLink] = metric.documentationLink
                    it[minValue] = metric.minValue
                    it[maxValue] = metric.maxValue
                    it[higherIsBetter] = metric.higherIsBetter
                }

                checkNotNull(findByMetric(metric))
            }

        private fun sha256Eq(column: Column<String?>, value: String?): Op<Boolean> =
            if (value == null) {
                column.isNull()
            } else {
                Sha256TextFunction(column) eq Sha256TextFunction(stringLiteral(value))
            }
    }
}
