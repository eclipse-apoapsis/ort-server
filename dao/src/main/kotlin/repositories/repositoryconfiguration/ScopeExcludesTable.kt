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

package org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration

import org.eclipse.apoapsis.ortserver.model.runs.repository.ScopeExclude

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent a scope exclude.
 */
object ScopeExcludesTable : LongIdTable("scope_excludes") {
    val pattern = text("pattern")
    val reason = text("reason")
    val comment = text("comment")
}

class ScopeExcludeDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScopeExcludeDao>(ScopeExcludesTable) {
        fun findByScopeExclude(scopeExclude: ScopeExclude): ScopeExcludeDao? =
            find {
                ScopeExcludesTable.pattern eq scopeExclude.pattern and
                        (ScopeExcludesTable.reason eq scopeExclude.reason) and
                        (ScopeExcludesTable.comment eq scopeExclude.comment)
            }.firstOrNull()

        fun getOrPut(scopeExclude: ScopeExclude): ScopeExcludeDao =
            findByScopeExclude(scopeExclude) ?: new {
                pattern = scopeExclude.pattern
                reason = scopeExclude.reason
                comment = scopeExclude.comment
            }
    }

    var pattern by ScopeExcludesTable.pattern
    var reason by ScopeExcludesTable.reason
    var comment by ScopeExcludesTable.comment

    fun mapToModel() = ScopeExclude(pattern, reason, comment)
}
