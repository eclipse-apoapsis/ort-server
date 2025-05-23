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

package org.eclipse.apoapsis.ortserver.dao.queries.environment

import org.eclipse.apoapsis.ortserver.dao.Query
import org.eclipse.apoapsis.ortserver.dao.tables.shared.EnvironmentsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.EnvironmentsToolVersionsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.EnvironmentsVariablesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.ToolVersionsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VariablesTable
import org.eclipse.apoapsis.ortserver.model.runs.Environment

import org.jetbrains.exposed.sql.selectAll

/** A query to get an [Environment] by its ID. Returns `null` if the environment is not found. */
class GetEnvironmentQuery(
    /** The ID of the environment to retrieve. */
    val environmentId: Long
) : Query<Environment?> {
    override fun execute(): Environment? {
        val resultRow = EnvironmentsTable.selectAll().where { EnvironmentsTable.id eq environmentId }.singleOrNull()

        if (resultRow == null) return null

        val toolVersions = EnvironmentsToolVersionsTable
            .innerJoin(ToolVersionsTable)
            .select(ToolVersionsTable.name, ToolVersionsTable.version)
            .where { EnvironmentsToolVersionsTable.environmentId eq environmentId }.associate {
                it[ToolVersionsTable.name] to it[ToolVersionsTable.version]
            }

        val variables = EnvironmentsVariablesTable
            .innerJoin(VariablesTable)
            .select(VariablesTable.name, VariablesTable.value)
            .where { EnvironmentsVariablesTable.environmentId eq environmentId }.associate {
                it[VariablesTable.name] to it[VariablesTable.value]
            }

        return Environment(
            ortVersion = resultRow[EnvironmentsTable.ortVersion],
            javaVersion = resultRow[EnvironmentsTable.javaVersion],
            os = resultRow[EnvironmentsTable.os],
            processors = resultRow[EnvironmentsTable.processors],
            maxMemory = resultRow[EnvironmentsTable.maxMemory],
            variables = variables,
            toolVersions = toolVersions
        )
    }
}
