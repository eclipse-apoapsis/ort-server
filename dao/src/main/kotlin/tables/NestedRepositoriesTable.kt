/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.tables

import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to represent a relation between nested repositories and ORT runs.
 */
object NestedRepositoriesTable : LongIdTable("nested_repositories") {
    val ortRunId = reference("ort_run_id", OrtRunsTable)
    val vcsId = reference("vcs_id", VcsInfoTable)
    val path = text("path")
}

class NestedRepositoryDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<NestedRepositoryDao>(NestedRepositoriesTable)

    var vcsId by NestedRepositoriesTable.vcsId
    var path by NestedRepositoriesTable.path
}
