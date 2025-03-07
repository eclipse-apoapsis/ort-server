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

import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent a rule violation resolution, used within a
 * [RepositoryConfiguration][RepositoryConfigurationsTable].
 */
object RuleViolationResolutionsTable : LongIdTable("rule_violation_resolutions") {
    val message = text("message")
    val reason = text("reason")
    val comment = text("comment")
}

class RuleViolationResolutionDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<RuleViolationResolutionDao>(RuleViolationResolutionsTable) {
        fun findByRuleViolationResolutionDao(
            ruleViolationResolution: RuleViolationResolution
        ): RuleViolationResolutionDao? = find {
            RuleViolationResolutionsTable.message eq ruleViolationResolution.message and
                    (RuleViolationResolutionsTable.reason eq ruleViolationResolution.reason) and
                    (RuleViolationResolutionsTable.comment eq ruleViolationResolution.comment)
        }.firstOrNull()

        fun getOrPut(ruleViolationResolution: RuleViolationResolution): RuleViolationResolutionDao =
            findByRuleViolationResolutionDao(ruleViolationResolution) ?: new {
                message = ruleViolationResolution.message
                reason = ruleViolationResolution.reason
                comment = ruleViolationResolution.comment
            }
    }

    var message by RuleViolationResolutionsTable.message
    var reason by RuleViolationResolutionsTable.reason
    var comment by RuleViolationResolutionsTable.comment

    fun mapToModel() = RuleViolationResolution(message, reason, comment)
}
