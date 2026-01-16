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

package org.eclipse.apoapsis.ortserver.dao.tables.shared

import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.IssueResolutionsTable

import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to store resolved issues, linking [OrtRunsTable], [OrtRunsIssuesTable], and [IssueResolutionsTable].
 */
object ResolvedIssuesTable : LongIdTable("resolved_issues") {
    val ortRunId = reference("ort_run_id", OrtRunsTable)
    val ortRunIssueId = reference("ort_run_issue_id", OrtRunsIssuesTable)
    val issueResolutionId = reference("issue_resolution_id", IssueResolutionsTable)
}
