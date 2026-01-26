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

import org.eclipse.apoapsis.ortserver.model.runs.scanner.SnippetFinding
import org.eclipse.apoapsis.ortserver.model.runs.scanner.TextLocation

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

/**
 * A table to represent a snippet finding.
 */
object SnippetFindingsTable : LongIdTable("snippet_findings") {
    val path = text("path")
    val startLine = integer("start_line")
    val endLine = integer("end_line")
    val scanSummaryId = reference("scan_summary_id", ScanSummariesTable)
}

class SnippetFindingDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SnippetFindingDao>(SnippetFindingsTable)

    var path by SnippetFindingsTable.path
    var startLine by SnippetFindingsTable.startLine
    var endLine by SnippetFindingsTable.endLine
    var scanSummary by ScanSummaryDao referencedOn SnippetFindingsTable.scanSummaryId

    var snippets by SnippetDao via SnippetFindingsSnippetsTable

    fun mapToModel(): SnippetFinding = SnippetFinding(
        location = TextLocation(
            path = path,
            startLine = startLine,
            endLine = endLine
        ),
        snippets = snippets.mapTo(mutableSetOf(), SnippetDao::mapToModel)
    )
}
