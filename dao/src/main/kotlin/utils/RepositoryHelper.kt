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

package org.ossreviewtoolkit.server.dao.utils

import org.jetbrains.exposed.sql.insert

import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentsToolVersionsTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentsVariablesTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifierDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.OrtIssueDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.ToolVersionDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VariableDao
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.OrtIssue

internal fun getOrPutEnvironment(environment: Environment): EnvironmentDao =
    EnvironmentDao.findByEnvironment(environment) ?: EnvironmentDao.new {
        ortVersion = environment.ortVersion
        javaVersion = environment.javaVersion
        os = environment.os
        processors = environment.processors
        maxMemory = environment.maxMemory
    }.also { environmentDao ->
        environment.toolVersions.forEach { (name, version) ->
            val toolVersionDao = getOrPutToolVersion(name, version)

            EnvironmentsToolVersionsTable.insert {
                it[environmentId] = environmentDao.id
                it[toolVersionId] = toolVersionDao.id
            }
        }

        environment.variables.forEach { (name, value) ->
            val variableDao = getOrPutVariable(name, value)

            EnvironmentsVariablesTable.insert {
                it[environmentId] = environmentDao.id
                it[variableId] = variableDao.id
            }
        }
    }

internal fun getOrPutToolVersion(name: String, version: String): ToolVersionDao =
    ToolVersionDao.findByNameAndVersion(name, version) ?: ToolVersionDao.new {
        this.name = name
        this.version = version
    }

internal fun getOrPutVariable(name: String, value: String): VariableDao =
    VariableDao.findByNameAndValue(name, value) ?: VariableDao.new {
        this.name = name
        this.value = value
    }

internal fun getOrPutIdentifier(identifier: Identifier): IdentifierDao =
    IdentifierDao.findByIdentifier(identifier) ?: IdentifierDao.new {
        type = identifier.type
        namespace = identifier.namespace
        name = identifier.name
        version = identifier.version
    }

internal fun getOrPutIssue(issue: OrtIssue): OrtIssueDao =
    OrtIssueDao.findByIssue(issue) ?: OrtIssueDao.new {
        timestamp = issue.timestamp
        source = issue.source
        message = issue.message
        severity = issue.severity
    }
