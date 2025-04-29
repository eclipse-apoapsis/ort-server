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

package org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun

import org.eclipse.apoapsis.ortserver.dao.utils.SortableEntityClass
import org.eclipse.apoapsis.ortserver.dao.utils.SortableTable
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Vulnerability
import org.eclipse.apoapsis.ortserver.model.runs.advisor.VulnerabilityReference

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and

/**
 * A table to represent a vulnerability.
 */
object VulnerabilitiesTable : SortableTable("vulnerabilities") {
    val externalId = text("external_id").sortable("externalId")
    val summary = text("summary").nullable()
    val description = text("description").nullable()
}

class VulnerabilityDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : SortableEntityClass<VulnerabilityDao>(VulnerabilitiesTable) {
        /**
         * Find a vulnerability by its external ID and the related references in order to avoid duplicates.
         * Intentionally use the Exposed DSL API for finding the vulnerability to prevent excessive growth of the
         * EntityCache.
         */
        fun findByVulnerability(vulnerability: Vulnerability): VulnerabilityDao? =
            VulnerabilitiesTable
                .select(
                    VulnerabilitiesTable.id,
                )
                .where {
                    (VulnerabilitiesTable.externalId eq vulnerability.externalId) and
                            (VulnerabilitiesTable.summary eq vulnerability.summary) and
                            (VulnerabilitiesTable.description eq vulnerability.description)
                }
                .asSequence()
                .map { resultRow ->
                    val vulnerabilityId = resultRow[VulnerabilitiesTable.id]
                    val references = VulnerabilityReferencesTable
                        .select(
                            VulnerabilityReferencesTable.url,
                            VulnerabilityReferencesTable.scoringSystem,
                            VulnerabilityReferencesTable.severity,
                            VulnerabilityReferencesTable.score,
                            VulnerabilityReferencesTable.vector
                        )
                        .where { VulnerabilityReferencesTable.vulnerabilityId eq vulnerabilityId }
                        .map {
                            VulnerabilityReference(
                                it[VulnerabilityReferencesTable.url],
                                it[VulnerabilityReferencesTable.scoringSystem],
                                it[VulnerabilityReferencesTable.severity],
                                it[VulnerabilityReferencesTable.score],
                                it[VulnerabilityReferencesTable.vector]
                            )
                        }
                        .toSet()

                    Pair(vulnerabilityId, references)
                }
                .firstOrNull { (_, references) ->
                    references == vulnerability.references.toSet()
                }
                ?.let { (vulnerabilityId, _) ->
                    VulnerabilityDao[vulnerabilityId] // Return as DAO
                }

        fun getOrPut(vulnerability: Vulnerability): VulnerabilityDao =
            findByVulnerability(vulnerability) ?: new {
                externalId = vulnerability.externalId
                summary = vulnerability.summary
                description = vulnerability.description
            }.also {
                vulnerability.references.forEach { reference ->
                    VulnerabilityReferenceDao.new {
                        this.vulnerability = it
                        this.url = reference.url
                        this.scoringSystem = reference.scoringSystem
                        this.severity = reference.severity
                        this.score = reference.score
                        this.vector = reference.vector
                    }
                }
            }
    }

    var externalId by VulnerabilitiesTable.externalId
    var summary by VulnerabilitiesTable.summary
    var description by VulnerabilitiesTable.description

    val references by VulnerabilityReferenceDao referrersOn VulnerabilityReferencesTable.vulnerabilityId

    fun mapToModel() = Vulnerability(
        externalId = externalId,
        summary = summary,
        description = description,
        references = references.map(VulnerabilityReferenceDao::mapToModel)
    )
}
