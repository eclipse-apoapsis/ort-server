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

package org.eclipse.apoapsis.ortserver.dao.repositories.ortrun

import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.upsertReturning

/**
 * A table to store the last assigned ORT run index for each repository. It is used to allocate the indexes of new ORT
 * runs without race conditions, see [nextIndex].
 */
object OrtRunIndexesTable : Table("ort_run_indexes") {
    val repositoryId = reference("repository_id", RepositoriesTable, onDelete = ReferenceOption.CASCADE)
    val lastIndex = long("last_index")

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(repositoryId, name = "${tableName}_pkey")

    /**
     * Allocate and return the next ORT run index for the repository with the given [repositoryId]. The index is
     * incremented with a single upsert statement, which locks the row of the repository until the end of the current
     * transaction. Therefore, concurrent transactions allocating an index for the same repository are serialized
     * without causing serialization failures, as long as they use the "read committed" isolation level. If the
     * transaction is rolled back, the increment is rolled back as well, so no gaps in the indexes are created.
     */
    fun nextIndex(repositoryId: Long): Long =
        upsertReturning(
            OrtRunIndexesTable.repositoryId,
            returning = listOf(lastIndex),
            onUpdate = { it[lastIndex] = lastIndex + 1 }
        ) {
            it[this.repositoryId] = repositoryId
            it[lastIndex] = 1
        }.single()[lastIndex]
}
