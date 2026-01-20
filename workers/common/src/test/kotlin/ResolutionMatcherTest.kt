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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import java.time.Instant

import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedItemsResult
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.RuleViolation
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Vulnerability
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.VulnerabilityResolution

import org.ossreviewtoolkit.model.Identifier as OrtIdentifier
import org.ossreviewtoolkit.model.Issue as OrtIssue
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.RuleViolation as OrtRuleViolation
import org.ossreviewtoolkit.model.Severity as OrtSeverity
import org.ossreviewtoolkit.model.config.IssueResolution as OrtIssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason as OrtIssueResolutionReason
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution as OrtRuleViolationResolution
import org.ossreviewtoolkit.model.config.RuleViolationResolutionReason as OrtRuleViolationResolutionReason
import org.ossreviewtoolkit.model.config.VulnerabilityResolution as OrtVulnerabilityResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolutionReason as OrtVulnerabilityResolutionReason
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability as OrtVulnerability
import org.ossreviewtoolkit.utils.common.enumSetOf

class ResolutionMatcherTest : WordSpec({
    val timestamp = Instant.now()

    val ortIssue1 = OrtIssue(
        timestamp = timestamp,
        source = "Analyzer",
        message = "Could not resolve dependency.",
        severity = OrtSeverity.ERROR
    )

    val ortIssue2 = OrtIssue(
        timestamp = timestamp,
        source = "Scanner",
        message = "Timeout while scanning.",
        severity = OrtSeverity.WARNING
    )

    val ortIssueResolution1 = OrtIssueResolution(
        message = ".*resolve dependency.*",
        reason = OrtIssueResolutionReason.CANT_FIX_ISSUE,
        comment = "Known issue with this dependency."
    )

    val ortIssueResolution2 = OrtIssueResolution(
        message = "Timeout.*",
        reason = OrtIssueResolutionReason.SCANNER_ISSUE,
        comment = "Scanner timeout is acceptable."
    )

    val ortRuleViolation1 = OrtRuleViolation(
        rule = "NO_UNKNOWN_LICENSE",
        pkg = OrtIdentifier("Maven:com.example:package:1.0"),
        license = null,
        licenseSources = enumSetOf<LicenseSource>(),
        severity = OrtSeverity.ERROR,
        message = "Package has unknown license.",
        howToFix = "Add license information."
    )

    val ortRuleViolation2 = OrtRuleViolation(
        rule = "NO_GPL_LICENSE",
        pkg = OrtIdentifier("Maven:com.example:gpl-package:2.0"),
        license = null,
        licenseSources = enumSetOf<LicenseSource>(),
        severity = OrtSeverity.WARNING,
        message = "Package uses GPL license.",
        howToFix = "Review license compatibility."
    )

    val ortRuleViolationResolution1 = OrtRuleViolationResolution(
        message = ".*unknown license.*",
        reason = OrtRuleViolationResolutionReason.CANT_FIX_EXCEPTION,
        comment = "License will be added later."
    )

    val ortVulnerability1 = OrtVulnerability(
        id = "CVE-2023-0001",
        summary = "Example vulnerability",
        description = "A test vulnerability.",
        references = emptyList()
    )

    val ortVulnerability2 = OrtVulnerability(
        id = "CVE-2023-0002",
        summary = "Another vulnerability",
        description = "Another test vulnerability.",
        references = emptyList()
    )

    val ortVulnerabilityResolution1 = OrtVulnerabilityResolution(
        id = "CVE-2023-0001",
        reason = OrtVulnerabilityResolutionReason.INEFFECTIVE_VULNERABILITY,
        comment = "Not exploitable in our context."
    )

    val expectedIssueResolution1 = IssueResolution(
        message = ".*resolve dependency.*",
        reason = "CANT_FIX_ISSUE",
        comment = "Known issue with this dependency."
    )

    val expectedIssueResolution2 = IssueResolution(
        message = "Timeout.*",
        reason = "SCANNER_ISSUE",
        comment = "Scanner timeout is acceptable."
    )

    val expectedRuleViolationResolution1 = RuleViolationResolution(
        message = ".*unknown license.*",
        reason = "CANT_FIX_EXCEPTION",
        comment = "License will be added later."
    )

    val expectedVulnerabilityResolution1 = VulnerabilityResolution(
        externalId = "CVE-2023-0001",
        reason = "INEFFECTIVE_VULNERABILITY",
        comment = "Not exploitable in our context."
    )

    "resolveResolutionsWithMappings" should {
        "match issues with resolutions using regex and return model types" {
            val resolutions = Resolutions(issues = listOf(ortIssueResolution1, ortIssueResolution2))
            val resolutionProvider = DefaultResolutionProvider(resolutions)

            val result = resolveResolutionsWithMappings(
                issues = listOf(ortIssue1, ortIssue2),
                ruleViolations = emptyList(),
                vulnerabilities = emptyList(),
                resolutionProvider = resolutionProvider
            )

            result.issues shouldHaveSize 2
            val issues = result.issues.keys.toList()
            issues.forEach { (it as? Issue) shouldNotBe null }
            val resolutionsForIssue1 = result.issues.values.flatten()
            resolutionsForIssue1 shouldContainExactlyInAnyOrder listOf(
                expectedIssueResolution1,
                expectedIssueResolution2
            )
        }

        "match rule violations with resolutions using regex and return model types" {
            val resolutions = Resolutions(ruleViolations = listOf(ortRuleViolationResolution1))
            val resolutionProvider = DefaultResolutionProvider(resolutions)

            val result = resolveResolutionsWithMappings(
                issues = emptyList(),
                ruleViolations = listOf(ortRuleViolation1, ortRuleViolation2),
                vulnerabilities = emptyList(),
                resolutionProvider = resolutionProvider
            )

            result.ruleViolations shouldHaveSize 1
            val ruleViolations = result.ruleViolations.keys.toList()
            ruleViolations.forEach { (it as? RuleViolation) shouldNotBe null }
            result.ruleViolations.values.flatten() shouldBe listOf(expectedRuleViolationResolution1)
        }

        "match vulnerabilities with resolutions using exact id match and return model types" {
            val resolutions = Resolutions(vulnerabilities = listOf(ortVulnerabilityResolution1))
            val resolutionProvider = DefaultResolutionProvider(resolutions)

            val result = resolveResolutionsWithMappings(
                issues = emptyList(),
                ruleViolations = emptyList(),
                vulnerabilities = listOf(ortVulnerability1, ortVulnerability2),
                resolutionProvider = resolutionProvider
            )

            result.vulnerabilities shouldHaveSize 1
            val vulnerabilities = result.vulnerabilities.keys.toList()
            vulnerabilities.forEach { (it as? Vulnerability) shouldNotBe null }
            vulnerabilities.first().externalId shouldBe "CVE-2023-0001"
            result.vulnerabilities.values.flatten() shouldBe listOf(expectedVulnerabilityResolution1)
        }

        "return empty maps for items with no matching resolutions" {
            val resolutions = Resolutions()
            val resolutionProvider = DefaultResolutionProvider(resolutions)

            val result = resolveResolutionsWithMappings(
                issues = listOf(ortIssue1),
                ruleViolations = listOf(ortRuleViolation1),
                vulnerabilities = listOf(ortVulnerability1),
                resolutionProvider = resolutionProvider
            )

            result.issues.shouldBeEmpty()
            result.ruleViolations.shouldBeEmpty()
            result.vulnerabilities.shouldBeEmpty()
        }

        "return empty result for empty inputs" {
            val resolutions = Resolutions(
                issues = listOf(ortIssueResolution1),
                ruleViolations = listOf(ortRuleViolationResolution1),
                vulnerabilities = listOf(ortVulnerabilityResolution1)
            )
            val resolutionProvider = DefaultResolutionProvider(resolutions)

            val result = resolveResolutionsWithMappings(
                issues = emptyList(),
                ruleViolations = emptyList(),
                vulnerabilities = emptyList(),
                resolutionProvider = resolutionProvider
            )

            result shouldBe ResolvedItemsResult.EMPTY
        }
    }
})
