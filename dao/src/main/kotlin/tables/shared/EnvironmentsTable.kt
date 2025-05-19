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

package org.eclipse.apoapsis.ortserver.dao.tables.shared

import org.eclipse.apoapsis.ortserver.model.runs.Environment

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

/**
 * A table to represent the environment settings.
 */
object EnvironmentsTable : LongIdTable("environments") {
    val ortVersion = text("ort_version")
    val javaVersion = text("java_version")
    val os = text("os")
    val processors = integer("processors")
    val maxMemory = long("max_memory")

    /** Get the [Environment] for the given [id]. Returns `null` if no environment is found. */
    fun getById(id: Long): Environment? {
        val resultRow = selectAll().where { EnvironmentsTable.id eq id }.singleOrNull()

        if (resultRow == null) return null

        val variables = EnvironmentsVariablesTable.getVariablesByEnvironmentId(id)
        val toolVersions = EnvironmentsToolVersionsTable.getToolVersionsByEnvironmentId(id)

        return Environment(
            ortVersion = resultRow[ortVersion],
            javaVersion = resultRow[javaVersion],
            os = resultRow[os],
            processors = resultRow[processors],
            maxMemory = resultRow[maxMemory],
            variables = variables,
            toolVersions = toolVersions
        )
    }
}

class EnvironmentDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<EnvironmentDao>(EnvironmentsTable) {
        fun findByEnvironment(environment: Environment): EnvironmentDao? =
            // TODO: Implement are more efficient way to check if an identical environment already exists.
            find {
                EnvironmentsTable.ortVersion eq environment.ortVersion and
                        (EnvironmentsTable.javaVersion eq environment.javaVersion) and
                        (EnvironmentsTable.os eq environment.os) and
                        (EnvironmentsTable.processors eq environment.processors) and
                        (EnvironmentsTable.maxMemory eq environment.maxMemory)
            }.firstOrNull { dao ->
                dao.variables.associate { it.name to it.value } == environment.variables &&
                        dao.toolVersions.associate { it.name to it.version } == environment.toolVersions
            }

        fun getOrPut(environment: Environment): EnvironmentDao =
            findByEnvironment(environment) ?: new {
                ortVersion = environment.ortVersion
                javaVersion = environment.javaVersion
                os = environment.os
                processors = environment.processors
                maxMemory = environment.maxMemory
            }.also { environmentDao ->
                environment.toolVersions.forEach { (name, version) ->
                    val toolVersionDao = ToolVersionDao.getOrPut(name, version)

                    EnvironmentsToolVersionsTable.insert {
                        it[environmentId] = environmentDao.id
                        it[toolVersionId] = toolVersionDao.id
                    }
                }

                environment.variables.forEach { (name, value) ->
                    val variableDao = VariableDao.getOrPut(name, value)

                    EnvironmentsVariablesTable.insert {
                        it[environmentId] = environmentDao.id
                        it[variableId] = variableDao.id
                    }
                }
            }
    }

    var ortVersion by EnvironmentsTable.ortVersion
    var javaVersion by EnvironmentsTable.javaVersion
    var os by EnvironmentsTable.os
    var processors by EnvironmentsTable.processors
    var maxMemory by EnvironmentsTable.maxMemory

    val variables by VariableDao via EnvironmentsVariablesTable
    val toolVersions by ToolVersionDao via EnvironmentsToolVersionsTable

    fun mapToModel() = Environment(
        ortVersion = ortVersion,
        javaVersion = javaVersion,
        os = os,
        processors = processors,
        maxMemory = maxMemory,
        variables = variables.associate { it.name to it.value },
        toolVersions = toolVersions.associate { it.name to it.version }
    )
}
