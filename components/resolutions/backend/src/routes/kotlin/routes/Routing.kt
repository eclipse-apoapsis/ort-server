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

package org.eclipse.apoapsis.ortserver.components.resolutions.routes

import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.resolutions.issues.IssueResolutionService
import org.eclipse.apoapsis.ortserver.components.resolutions.routes.issues.deleteIssueResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.routes.issues.patchIssueResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.routes.issues.postIssueResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.routes.ruleviolations.deleteRuleViolationResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.routes.ruleviolations.patchRuleViolationResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.routes.ruleviolations.postRuleViolationResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.routes.vulnerabilities.deleteVulnerabilityResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.routes.vulnerabilities.patchVulnerabilityResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.routes.vulnerabilities.postVulnerabilityResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.ruleviolations.RuleViolationResolutionService
import org.eclipse.apoapsis.ortserver.components.resolutions.vulnerabilities.VulnerabilityResolutionService

fun Route.resolutionRoutes(
    issueResolutionService: IssueResolutionService,
    ruleViolationResolutionService: RuleViolationResolutionService,
    vulnerabilityResolutionService: VulnerabilityResolutionService
) {
    deleteIssueResolution(issueResolutionService)
    patchIssueResolution(issueResolutionService)
    postIssueResolution(issueResolutionService)
    deleteRuleViolationResolution(ruleViolationResolutionService)
    patchRuleViolationResolution(ruleViolationResolutionService)
    postRuleViolationResolution(ruleViolationResolutionService)
    deleteVulnerabilityResolution(vulnerabilityResolutionService)
    patchVulnerabilityResolution(vulnerabilityResolutionService)
    postVulnerabilityResolution(vulnerabilityResolutionService)
}
