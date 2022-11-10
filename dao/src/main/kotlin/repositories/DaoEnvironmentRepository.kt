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

package org.ossreviewtoolkit.server.dao.repositories

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentToolVersionDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentVariableDao
import org.ossreviewtoolkit.server.model.repositories.EnvironmentRepository

/**
 * An implementation of [EnvironmentRepository] that stores environnments in [EnvironmentsTable].
 */
class DaoEnvironmentRepository : EnvironmentRepository {
    override fun create(
        ortVersion: String,
        javaVersion: String,
        os: String,
        processors: Int,
        maxMemory: Long,
        variables: Map<String, String>,
        toolVersions: Map<String, String>
    ) = blockingQuery {
        val environmentDao = EnvironmentDao.new {
            this.ortVersion = ortVersion
            this.javaVersion = javaVersion
            this.os = os
            this.processors = processors
            this.maxMemory = maxMemory
        }

        variables.forEach { (name, value) ->
            EnvironmentVariableDao.new {
                environment = environmentDao
                this.name = name
                this.value = value
            }
        }

        toolVersions.forEach { (name, version) ->
            EnvironmentToolVersionDao.new {
                environment = environmentDao
                this.name = name
                this.version = version
            }
        }

        environmentDao.mapToModel()
    }.getOrThrow()

    override fun get(id: Long) = blockingQuery { EnvironmentDao[id].mapToModel() }.getOrNull()

    override fun list() = blockingQuery { EnvironmentDao.all().map { it.mapToModel() } }.getOrThrow()

    override fun delete(id: Long) = blockingQuery { EnvironmentDao[id].delete() }.getOrThrow()
}
