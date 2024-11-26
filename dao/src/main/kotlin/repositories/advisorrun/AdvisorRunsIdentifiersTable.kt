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

import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorResult

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * An intermediate table to store references from [AdvisorRunsTable] and [IdentifiersTable].
 */
object AdvisorRunsIdentifiersTable : LongIdTable("advisor_runs_identifiers") {
    val advisorRunId = reference("advisor_run_id", AdvisorRunsTable)
    val identifierId = reference("identifier_id", IdentifiersTable)
}

class AdvisorRunIdentifierDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AdvisorRunIdentifierDao>(AdvisorRunsIdentifiersTable)

    var advisorRun by AdvisorRunDao referencedOn AdvisorRunsIdentifiersTable.advisorRunId
    var identifier by IdentifierDao referencedOn AdvisorRunsIdentifiersTable.identifierId

    val advisorResults by AdvisorResultDao referrersOn AdvisorResultsTable.advisorRunIdentifierId

    fun mapToModel(runIssues: List<Issue>): Pair<Identifier, List<AdvisorResult>> {
        val modelIdentifier = identifier.mapToModel()
        val identifierIssues = runIssues.filter { it.identifier == modelIdentifier }

        return Pair(modelIdentifier, advisorResults.map { it.mapToModel(identifierIssues) })
    }
}
