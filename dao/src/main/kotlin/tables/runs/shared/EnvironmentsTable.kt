/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.tables.runs.shared

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert

import org.ossreviewtoolkit.server.model.runs.Environment

/**
 * A table to represent the environment settings.
 */
object EnvironmentsTable : LongIdTable("environments") {
    val ortVersion = text("ort_version")
    val javaVersion = text("java_version")
    val os = text("os")
    val processors = integer("processors")
    val maxMemory = long("max_memory")
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
            }.singleOrNull {
                it.variables.associate { it.name to it.value } == environment.variables &&
                        it.toolVersions.associate { it.name to it.version } == environment.toolVersions
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

    val variables by VariableDao via EnvironmentsVariablesTable
    val toolVersions by ToolVersionDao via EnvironmentsToolVersionsTable

    var ortVersion by EnvironmentsTable.ortVersion
    var javaVersion by EnvironmentsTable.javaVersion
    var os by EnvironmentsTable.os
    var processors by EnvironmentsTable.processors
    var maxMemory by EnvironmentsTable.maxMemory

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
