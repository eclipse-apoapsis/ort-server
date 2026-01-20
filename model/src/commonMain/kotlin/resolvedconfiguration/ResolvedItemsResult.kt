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

package org.eclipse.apoapsis.ortserver.model.resolvedconfiguration

import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.RuleViolation
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Vulnerability
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.VulnerabilityResolution

/**
 * A data class holding the result of resolution matching, mapping each item to its matching resolutions.
 * This is used to store pre-computed resolution matches in the database for efficient statistics queries.
 *
 */
data class ResolvedItemsResult(
    /** Map of issues to the resolutions that matched them. */
    val issues: Map<Issue, List<IssueResolution>> = emptyMap(),

    /** Map of rule violations to the resolutions that matched them. */
    val ruleViolations: Map<RuleViolation, List<RuleViolationResolution>> = emptyMap(),

    /** Map of vulnerabilities to the resolutions that matched them. */
    val vulnerabilities: Map<Vulnerability, List<VulnerabilityResolution>> = emptyMap()
) {
    companion object {
        val EMPTY = ResolvedItemsResult()
    }
}
