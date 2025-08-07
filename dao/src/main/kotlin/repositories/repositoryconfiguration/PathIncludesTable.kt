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

package org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration

import org.eclipse.apoapsis.ortserver.model.runs.repository.PathInclude

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent a path include, used within a [RepositoryConfiguration][RepositoryConfigurationsTable].
 */
object PathIncludesTable : LongIdTable("path_includes") {
    val pattern = text("pattern")
    val reason = text("reason")
    val comment = text("comment")
}

class PathIncludeDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PathIncludeDao>(PathIncludesTable) {
        fun findByPathInclude(pathInclude: PathInclude): PathIncludeDao? =
            find {
                PathIncludesTable.pattern eq pathInclude.pattern and
                        (PathIncludesTable.reason eq pathInclude.reason) and
                        (PathIncludesTable.comment eq pathInclude.comment)
            }.firstOrNull()

        fun getOrPut(pathInclude: PathInclude): PathIncludeDao =
            findByPathInclude(pathInclude) ?: new {
                pattern = pathInclude.pattern
                reason = pathInclude.reason
                comment = pathInclude.comment
            }
    }

    var pattern by PathIncludesTable.pattern
    var reason by PathIncludesTable.reason
    var comment by PathIncludesTable.comment

    fun mapToModel() = PathInclude(pattern, reason, comment)
}
