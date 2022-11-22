/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.tables.runs.analyzer

import org.jetbrains.exposed.sql.Table

import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifiersOrtIssuesTable

/**
 * An intermediate table to store references from [AnalyzerRunsTable] and [IdentifiersOrtIssuesTable].
 */
object AnalyzerRunsIdentifiersOrtIssuesTable : Table("analyzer_runs_identifiers_ort_issues") {
    val analyzerRunId = reference("analyzer_run_id", AnalyzerRunsTable)
    val identifierOrtIssueId = reference("identifier_ort_issue_id", IdentifiersOrtIssuesTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(analyzerRunId, identifierOrtIssueId, name = "${tableName}_pkey")
}
