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

package org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun

import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.utils.SortableEntityClass
import org.eclipse.apoapsis.ortserver.dao.utils.SortableTable
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.RuleViolation

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and

/**
 * A table to represent a rule violation.
 */
object RuleViolationsTable : SortableTable("rule_violations") {
    val rule = text("rule").sortable()
    val identifierId = reference("identifier_id", IdentifiersTable).nullable()
    val license = text("license").nullable()
    val licenseSource = text("license_source").nullable()
    val severity = enumerationByName<Severity>("severity", 128).sortable()
    val message = text("message")
    val howToFix = text("how_to_fix")
}

class RuleViolationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : SortableEntityClass<RuleViolationDao>(RuleViolationsTable) {
        fun getOrPut(ruleViolation: RuleViolation): RuleViolationDao =
            findByRuleViolation(ruleViolation) ?: new {
                rule = ruleViolation.rule
                identifierId = getIdentifierDaoOrNull(ruleViolation)
                license = ruleViolation.license
                licenseSource = ruleViolation.licenseSource
                severity = ruleViolation.severity
                message = ruleViolation.message
                howToFix = ruleViolation.howToFix
            }

        private fun findByRuleViolation(ruleViolation: RuleViolation): RuleViolationDao? {
            val identifierDao = getIdentifierDaoOrNull(ruleViolation)

            return find {
                RuleViolationsTable.rule eq ruleViolation.rule and
                        (RuleViolationsTable.identifierId eq identifierDao?.id) and
                        (RuleViolationsTable.license eq ruleViolation.license) and
                        (RuleViolationsTable.licenseSource eq ruleViolation.licenseSource) and
                        (RuleViolationsTable.severity eq ruleViolation.severity)
            }.find { it.message == ruleViolation.message && it.howToFix == ruleViolation.howToFix }
        }

        private fun getIdentifierDaoOrNull(ruleViolation: RuleViolation): IdentifierDao? {
            val identifier = ruleViolation.id
            return when {
                identifier != null -> IdentifierDao.findByIdentifier(identifier)
                else -> null
            }
        }
    }

    var rule by RuleViolationsTable.rule
    var identifierId by IdentifierDao optionalReferencedOn RuleViolationsTable.identifierId
    var license by RuleViolationsTable.license
    var licenseSource by RuleViolationsTable.licenseSource
    var severity by RuleViolationsTable.severity
    var message by RuleViolationsTable.message
    var howToFix by RuleViolationsTable.howToFix

    fun mapToModel() = RuleViolation(
        rule = rule,
        id = identifierId?.mapToModel(),
        license = license,
        licenseSource = licenseSource,
        severity = severity,
        message = message,
        howToFix = howToFix,
    )
}
