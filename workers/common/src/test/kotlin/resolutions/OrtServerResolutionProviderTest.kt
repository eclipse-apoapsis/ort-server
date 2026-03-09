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

package org.eclipse.apoapsis.ortserver.workers.common.resolutions

import com.github.michaelbull.result.Ok

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import io.mockk.every
import io.mockk.mockk

import java.io.ByteArrayInputStream

import org.eclipse.apoapsis.ortserver.components.resolutions.vulnerabilities.VulnerabilityResolutionService
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.runs.repository.ResolutionSource
import org.eclipse.apoapsis.ortserver.model.runs.repository.VulnerabilityResolution as ServerVulnerabilityResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.VulnerabilityResolutionReason as ServerVulnerabilityResolutionReason
import org.eclipse.apoapsis.ortserver.services.config.AdminConfig
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.RuleViolationResolutionReason
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolutionReason
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME

class OrtServerResolutionProviderTest : WordSpec({
    "create" should {
        "create a provider with merged resolutions from all three sources" {
            // Prepare global resolutions.
            val globalResolutionsYaml = """
                ---
                issues:
                  - message: match
                    reason: CANT_FIX_ISSUE
                    comment: matching global issue resolution
                rule_violations:
                  - message: match
                    reason: CANT_FIX_EXCEPTION
                    comment: matching global rule violation resolution
                vulnerabilities:                    
                  - id: match
                    reason: CANT_FIX_VULNERABILITY
                    comment: matching global vulnerability resolution
            """.trimIndent()

            val context = mockk<WorkerContext> {
                every { ortRun } returns mockk(relaxed = true) {
                    every { resolvedJobConfigs } returns null
                }

                every { configManager } returns mockk {
                    every { getFile(any(), Path(ORT_RESOLUTIONS_FILENAME)) } returns
                            ByteArrayInputStream(globalResolutionsYaml.toByteArray())
                }
            }

            val adminConfigService = mockk<AdminConfigService> {
                every { loadAdminConfig(any(), any()) } returns AdminConfig.DEFAULT
            }

            // Prepare repository configuration resolutions.
            val repositoryConfigurationResolutions = Resolutions(
                issues = listOf(
                    IssueResolution(
                        message = "match",
                        reason = IssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "matching repository configuration issue resolution"
                    )
                ),
                ruleViolations = listOf(
                    RuleViolationResolution(
                        message = "match",
                        reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "matching repository configuration rule violation resolution"
                    )
                ),
                vulnerabilities = listOf(
                    VulnerabilityResolution(
                        id = "match",
                        reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                        comment = "matching repository configuration vulnerability resolution"
                    )
                )
            )

            // Prepare managed resolutions.
            val repositoryId = RepositoryId(1)
            val vulnerabilityResolutionService = mockk<VulnerabilityResolutionService> {
                every { getResolutionsForRepository(repositoryId) } returns
                        Ok(
                            listOf(
                                ServerVulnerabilityResolution(
                                    externalId = "match",
                                    reason = ServerVulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                                    comment = "matching managed vulnerability resolution",
                                    source = ResolutionSource.REPOSITORY_FILE
                                )
                            )
                        )
            }

            val provider = OrtServerResolutionProvider.create(
                context,
                adminConfigService,
                repositoryConfigurationResolutions,
                repositoryId,
                vulnerabilityResolutionService
            )

            // Validate issue resolutions.
            val issue = Issue(source = "source", message = "match")

            val expectedIssueResolutions = listOf(
                IssueResolution(
                    message = "match",
                    reason = IssueResolutionReason.CANT_FIX_ISSUE,
                    comment = "matching global issue resolution"
                ),
                IssueResolution(
                    message = "match",
                    reason = IssueResolutionReason.CANT_FIX_ISSUE,
                    comment = "matching repository configuration issue resolution"
                )
            )

            provider.getResolutionsFor(issue) should containExactlyInAnyOrder(expectedIssueResolutions)

            // Validate rule violation resolutions.
            val ruleViolation = RuleViolation(
                rule = "rule",
                pkg = null,
                license = null,
                licenseSources = enumSetOf(),
                severity = Severity.WARNING,
                message = "match",
                howToFix = ""
            )

            val expectedRuleViolationResolutions = listOf(
                RuleViolationResolution(
                    message = "match",
                    reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                    comment = "matching global rule violation resolution"
                ),
                RuleViolationResolution(
                    message = "match",
                    reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                    comment = "matching repository configuration rule violation resolution"
                )
            )

            provider.getResolutionsFor(ruleViolation) should containExactlyInAnyOrder(expectedRuleViolationResolutions)

            // Validate vulnerability resolutions.
            val vulnerability = Vulnerability(id = "match", references = emptyList())

            val expectedVulnerabilityResolutions = listOf(
                VulnerabilityResolution(
                    id = "match",
                    reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                    comment = "matching global vulnerability resolution"
                ),
                VulnerabilityResolution(
                    id = "match",
                    reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                    comment = "matching repository configuration vulnerability resolution"
                ),
                VulnerabilityResolution(
                    id = "match",
                    reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                    comment = "matching managed vulnerability resolution"
                )
            )

            provider.getResolutionsFor(vulnerability) should containExactlyInAnyOrder(expectedVulnerabilityResolutions)
        }
    }

    "getResolutionsFor" should {
        "return matching issue resolutions from all three sources" {
            val globalResolutions = Resolutions(
                issues = listOf(
                    IssueResolution(
                        message = "match",
                        reason = IssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "matching global issue resolution"
                    ),
                    IssueResolution(
                        message = "no match",
                        reason = IssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "non-matching global issue resolution"
                    )
                )
            )

            val repositoryConfigurationResolutions = Resolutions(
                issues = listOf(
                    IssueResolution(
                        message = "match",
                        reason = IssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "matching repository configuration issue resolution"
                    ),
                    IssueResolution(
                        message = "no match",
                        reason = IssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "non-matching repository configuration issue resolution"
                    )
                )
            )

            val managedResolutions = Resolutions(
                issues = listOf(
                    IssueResolution(
                        message = "match",
                        reason = IssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "matching managed issue resolution"
                    ),
                    IssueResolution(
                        message = "no match",
                        reason = IssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "non-matching managed issue resolution"
                    )
                )
            )

            val provider = OrtServerResolutionProvider(
                globalResolutions = globalResolutions,
                repositoryConfigurationResolutions = repositoryConfigurationResolutions,
                managedResolutions = managedResolutions
             )

            val issue = Issue(source = "source", message = "match")

            val expectedMatchingResolutions = listOf(
                globalResolutions.issues.first(),
                repositoryConfigurationResolutions.issues.first(),
                managedResolutions.issues.first()
            )

            provider.getResolutionsFor(issue) should containExactlyInAnyOrder(expectedMatchingResolutions)
        }

        "return matching rule violation resolutions from all three sources" {
            val globalResolutions = Resolutions(
                ruleViolations = listOf(
                    RuleViolationResolution(
                        message = "match",
                        reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "matching global rule violation resolution"
                    ),
                    RuleViolationResolution(
                        message = "no match",
                        reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "non-matching global rule violation resolution"
                    )
                )
            )

            val repositoryConfigurationResolutions = Resolutions(
                ruleViolations = listOf(
                    RuleViolationResolution(
                        message = "match",
                        reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "matching repository configuration rule violation resolution"
                    ),
                    RuleViolationResolution(
                        message = "no match",
                        reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "non-matching repository configuration rule violation resolution"
                    )
                )
            )

            val managedResolutions = Resolutions(
                ruleViolations = listOf(
                    RuleViolationResolution(
                        message = "match",
                        reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "matching managed rule violation resolution"
                    ),
                    RuleViolationResolution(
                        message = "no match",
                        reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "non-matching managed rule violation resolution"
                    )
                )
            )

            val provider = OrtServerResolutionProvider(
                globalResolutions = globalResolutions,
                repositoryConfigurationResolutions = repositoryConfigurationResolutions,
                managedResolutions = managedResolutions
            )

            val ruleViolation = RuleViolation(
                rule = "rule",
                pkg = null,
                license = null,
                licenseSources = enumSetOf(),
                severity = Severity.WARNING,
                message = "match",
                howToFix = ""
            )

            val expectedMatchingResolutions = listOf(
                globalResolutions.ruleViolations.first(),
                repositoryConfigurationResolutions.ruleViolations.first(),
                managedResolutions.ruleViolations.first()
            )

            provider.getResolutionsFor(ruleViolation) should containExactlyInAnyOrder(expectedMatchingResolutions)
        }

        "return matching vulnerability resolutions from all three sources" {
            val globalResolutions = Resolutions(
                vulnerabilities = listOf(
                    VulnerabilityResolution(
                        id = "match",
                        reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                        comment = "matching global vulnerability resolution"
                    ),
                    VulnerabilityResolution(
                        id = "no match",
                        reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                        comment = "non-matching global vulnerability resolution"
                    )
                )
            )

            val repositoryConfigurationResolutions = Resolutions(
                vulnerabilities = listOf(
                    VulnerabilityResolution(
                        id = "match",
                        reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                        comment = "matching repository configuration vulnerability resolution"
                    ),
                    VulnerabilityResolution(
                        id = "no match",
                        reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                        comment = "non-matching repository configuration vulnerability resolution"
                    )
                )
            )

            val managedResolutions = Resolutions(
                vulnerabilities = listOf(
                    VulnerabilityResolution(
                        id = "match",
                        reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                        comment = "matching managed vulnerability resolution"
                    ),
                    VulnerabilityResolution(
                        id = "no match",
                        reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                        comment = "non-matching managed vulnerability resolution"
                    )
                )
            )

            val provider = OrtServerResolutionProvider(
                globalResolutions = globalResolutions,
                repositoryConfigurationResolutions = repositoryConfigurationResolutions,
                managedResolutions = managedResolutions
            )

            val vulnerability = Vulnerability(id = "match", references = emptyList())

            val expectedMatchingResolutions = listOf(
                globalResolutions.vulnerabilities.first(),
                repositoryConfigurationResolutions.vulnerabilities.first(),
                managedResolutions.vulnerabilities.first()
            )

            provider.getResolutionsFor(vulnerability) should containExactlyInAnyOrder(expectedMatchingResolutions)
        }
    }
})
