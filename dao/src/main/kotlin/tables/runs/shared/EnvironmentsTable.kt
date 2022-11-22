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
    companion object : LongEntityClass<EnvironmentDao>(EnvironmentsTable)

    var ortVersion by EnvironmentsTable.ortVersion
    var javaVersion by EnvironmentsTable.javaVersion
    var os by EnvironmentsTable.os
    var processors by EnvironmentsTable.processors
    var maxMemory by EnvironmentsTable.maxMemory
    val variables by EnvironmentVariableDao referrersOn EnvironmentVariablesTable.environmentId
    val toolVersions by EnvironmentToolVersionDao referrersOn EnvironmentToolVersionsTable.environmentId

    fun mapToModel() = Environment(
        id.value,
        ortVersion,
        javaVersion,
        os,
        processors,
        maxMemory,
        variables.associate { it.name to it.value },
        toolVersions.associate { it.name to it.version }
    )
}
