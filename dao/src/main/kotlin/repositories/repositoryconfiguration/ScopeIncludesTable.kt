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

package org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration

import org.eclipse.apoapsis.ortserver.model.runs.repository.ScopeInclude

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

/**
 * A table to represent a scope include.
 */
object ScopeIncludesTable : LongIdTable("scope_includes") {
    val pattern = text("pattern")
    val reason = text("reason")
    val comment = text("comment")
}

class ScopeIncludeDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScopeIncludeDao>(ScopeIncludesTable) {
        fun findByScopeInclude(scopeInclude: ScopeInclude): ScopeIncludeDao? =
            find {
                ScopeIncludesTable.pattern eq scopeInclude.pattern and
                        (ScopeIncludesTable.reason eq scopeInclude.reason) and
                        (ScopeIncludesTable.comment eq scopeInclude.comment)
            }.firstOrNull()

        fun getOrPut(scopeInclude: ScopeInclude): ScopeIncludeDao =
            findByScopeInclude(scopeInclude) ?: new {
                pattern = scopeInclude.pattern
                reason = scopeInclude.reason
                comment = scopeInclude.comment
            }
    }

    var pattern by ScopeIncludesTable.pattern
    var reason by ScopeIncludesTable.reason
    var comment by ScopeIncludesTable.comment

    fun mapToModel() = ScopeInclude(pattern, reason, comment)
}
