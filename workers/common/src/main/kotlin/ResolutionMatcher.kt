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

package org.eclipse.apoapsis.ortserver.workers.common

import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedItemsResult
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToModel

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.utils.ResolutionProvider
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability

/**
 * Match issues, rule violations, and vulnerabilities against a [ResolutionProvider] and return the mappings
 * of each item to its matching resolutions. This is similar to ORT's ConfigurationResolver.resolveResolutions,
 * but returns the full mappings instead of just the distinct resolutions.
 *
 * The returned [ResolvedItemsResult] uses ORT Server model types (not ORT library types).
 */
fun resolveResolutionsWithMappings(
    issues: List<Issue>,
    ruleViolations: List<RuleViolation>,
    vulnerabilities: List<Vulnerability>,
    resolutionProvider: ResolutionProvider
): ResolvedItemsResult {
    val issueResolutions = issues.associateWith { issue ->
        resolutionProvider.getResolutionsFor(issue)
    }.filterValues { it.isNotEmpty() }

    val ruleViolationResolutions = ruleViolations.associateWith { violation ->
        resolutionProvider.getResolutionsFor(violation)
    }.filterValues { it.isNotEmpty() }

    val vulnerabilityResolutions = vulnerabilities.associateWith { vulnerability ->
        resolutionProvider.getResolutionsFor(vulnerability)
    }.filterValues { it.isNotEmpty() }

    return ResolvedItemsResult(
        issues = issueResolutions.map { (issue, resolutions) ->
            issue.mapToModel() to resolutions.map { it.mapToModel() }
        }.toMap(),
        ruleViolations = ruleViolationResolutions.map { (violation, resolutions) ->
            violation.mapToModel() to resolutions.map { it.mapToModel() }
        }.toMap(),
        vulnerabilities = vulnerabilityResolutions.map { (vulnerability, resolutions) ->
            vulnerability.mapToModel() to resolutions.map { it.mapToModel() }
        }.toMap()
    )
}
