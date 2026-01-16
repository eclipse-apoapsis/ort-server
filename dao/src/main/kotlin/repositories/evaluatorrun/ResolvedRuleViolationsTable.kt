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

package org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun

import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.RuleViolationResolutionsTable

import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to store resolved rule violations, linking [OrtRunsTable], [RuleViolationsTable],
 * and [RuleViolationResolutionsTable].
 */
object ResolvedRuleViolationsTable : LongIdTable("resolved_rule_violations") {
    val ortRunId = reference("ort_run_id", OrtRunsTable)
    val ruleViolationId = reference("rule_violation_id", RuleViolationsTable)
    val ruleViolationResolutionId = reference("rule_violation_resolution_id", RuleViolationResolutionsTable)
}
