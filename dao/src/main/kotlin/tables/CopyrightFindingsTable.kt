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

import org.eclipse.apoapsis.ortserver.model.runs.scanner.CopyrightFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.TextLocation

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

/**
 * A table to represent a copyright finding.
 */
object CopyrightFindingsTable : LongIdTable("copyright_findings") {
    val statement = text("statement")
    val path = text("path")
    val startLine = integer("start_line")
    val endLine = integer("end_line")
    val scanSummaryId = reference("scan_summary_id", ScanSummariesTable)
}

class CopyrightFindingDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<CopyrightFindingDao>(CopyrightFindingsTable)

    var statement by CopyrightFindingsTable.statement
    var path by CopyrightFindingsTable.path
    var startLine by CopyrightFindingsTable.startLine
    var endLine by CopyrightFindingsTable.endLine
    var scanSummary by ScanSummaryDao referencedOn CopyrightFindingsTable.scanSummaryId

    fun mapToModel() = CopyrightFinding(
        statement = statement,
        location = TextLocation(
            path = path,
            startLine = startLine,
            endLine = endLine
        )
    )
}
