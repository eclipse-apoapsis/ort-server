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

package org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun

import org.jetbrains.exposed.sql.Table

/**
 * An intermediate table to store references from [ProjectsTable] and [AuthorsTable].
 */
object ProjectsAuthorsTable : Table("projects_authors") {
    val authorId = reference("author_id", AuthorsTable)
    val projectId = reference("project_id", ProjectsTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(authorId, projectId, name = "${tableName}_pkey")

    /** Get the authors for the provided [projectIds]. */
    fun getAuthorsByProjectIds(projectIds: Set<Long>): Map<Long, Set<String>> =
        innerJoin(AuthorsTable)
            .select(projectId, AuthorsTable.name)
            .where { projectId inList projectIds }
            .groupBy({ it[projectId] }) { it[AuthorsTable.name] }
            .mapKeys { it.key.value }
            .mapValues { it.value.toSet() }
}
