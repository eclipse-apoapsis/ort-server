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
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.maps.containExactly
import io.kotest.matchers.should

import io.mockk.every
import io.mockk.mockk

import java.io.ByteArrayInputStream

import org.eclipse.apoapsis.ortserver.components.resolutions.issues.IssueResolutionService
import org.eclipse.apoapsis.ortserver.components.resolutions.ruleviolations.RuleViolationResolutionService
import org.eclipse.apoapsis.ortserver.components.resolutions.vulnerabilities.VulnerabilityResolutionService
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.dao.utils.calculateResolutionMessageHash
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution as ServerIssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolutionReason as ServerIssueResolutionReason
import org.eclipse.apoapsis.ortserver.model.runs.repository.ResolutionSource
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution as ServerRuleViolationResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolutionReason as ServerRuleViolationResolutionReason
import org.eclipse.apoapsis.ortserver.model.runs.repository.VulnerabilityResolution as ServerVulnerabilityResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.VulnerabilityResolutionReason as ServerVulnerabilityResolutionReason
import org.eclipse.apoapsis.ortserver.services.config.AdminConfig
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToModel
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

@Suppress("LargeClass")
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
            val issueResolutionService = mockk<IssueResolutionService> {
                every { getResolutionsForRepository(repositoryId) } returns
                        Ok(
                            listOf(
                                ServerIssueResolution(
                                    message = "match",
                                    messageHash = calculateResolutionMessageHash("match"),
                                    reason = ServerIssueResolutionReason.CANT_FIX_ISSUE,
                                    comment = "matching managed issue resolution",
                                    source = ResolutionSource.SERVER
                                )
                            )
                        )
            }
            val ruleViolationResolutionService = mockk<RuleViolationResolutionService> {
                every { getResolutionsForRepository(repositoryId) } returns
                        Ok(
                            listOf(
                                ServerRuleViolationResolution(
                                    message = "match",
                                    messageHash = calculateResolutionMessageHash("match"),
                                    reason = ServerRuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                                    comment = "matching managed rule violation resolution",
                                    source = ResolutionSource.SERVER
                                )
                            )
                        )
            }
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
                issueResolutionService,
                vulnerabilityResolutionService,
                ruleViolationResolutionService
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
                ),
                IssueResolution(
                    message = "match",
                    reason = IssueResolutionReason.CANT_FIX_ISSUE,
                    comment = "matching managed issue resolution"
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
                ),
                RuleViolationResolution(
                    message = "match",
                    reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                    comment = "matching managed rule violation resolution"
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

        "match managed issue resolutions by message hash even when the message is not a valid literal regex" {
            val context = mockk<WorkerContext> {
                every { ortRun } returns mockk(relaxed = true) {
                    every { resolvedJobConfigs } returns null
                }

                every { configManager } returns mockk {
                    every { getFile(any(), Path(ORT_RESOLUTIONS_FILENAME)) } returns
                            ByteArrayInputStream("{}\n".toByteArray())
                }
            }

            val adminConfigService = mockk<AdminConfigService> {
                every { loadAdminConfig(any(), any()) } returns AdminConfig.DEFAULT
            }

            val repositoryId = RepositoryId(1)
            val literalMessage = "BuildOperationRunner\$1.execute(ClassReader.java:199)"
            val issueResolutionService = mockk<IssueResolutionService> {
                every { getResolutionsForRepository(repositoryId) } returns
                        Ok(
                            listOf(
                                ServerIssueResolution(
                                    message = literalMessage,
                                    messageHash = calculateResolutionMessageHash(literalMessage),
                                    reason = ServerIssueResolutionReason.CANT_FIX_ISSUE,
                                    comment = "matching managed issue resolution",
                                    source = ResolutionSource.SERVER
                                )
                            )
                        )
            }

            val provider = OrtServerResolutionProvider.create(
                context,
                adminConfigService,
                repositoryConfigurationResolutions = Resolutions(),
                repositoryId = repositoryId,
                issueResolutionService = issueResolutionService
            )

            provider.getResolutionsFor(Issue(source = "source", message = literalMessage)) should
                    containExactlyInAnyOrder(
                        IssueResolution(
                            message = literalMessage,
                            reason = IssueResolutionReason.CANT_FIX_ISSUE,
                            comment = "matching managed issue resolution"
                        )
                    )
        }

        "match managed rule violation resolutions by message hash even when the message is not a valid literal regex" {
            val context = mockk<WorkerContext> {
                every { ortRun } returns mockk(relaxed = true) {
                    every { resolvedJobConfigs } returns null
                }

                every { configManager } returns mockk {
                    every { getFile(any(), Path(ORT_RESOLUTIONS_FILENAME)) } returns
                            ByteArrayInputStream("{}\n".toByteArray())
                }
            }

            val adminConfigService = mockk<AdminConfigService> {
                every { loadAdminConfig(any(), any()) } returns AdminConfig.DEFAULT
            }

            val repositoryId = RepositoryId(1)
            val literalMessage = "BuildOperationRunner\$1.execute(ClassReader.java:199)"
            val ruleViolationResolutionService = mockk<RuleViolationResolutionService> {
                every { getResolutionsForRepository(repositoryId) } returns
                        Ok(
                            listOf(
                                ServerRuleViolationResolution(
                                    message = literalMessage,
                                    messageHash = calculateResolutionMessageHash(literalMessage),
                                    reason = ServerRuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                                    comment = "matching managed rule violation resolution",
                                    source = ResolutionSource.SERVER
                                )
                            )
                        )
            }

            val provider = OrtServerResolutionProvider.create(
                context,
                adminConfigService,
                repositoryConfigurationResolutions = Resolutions(),
                repositoryId = repositoryId,
                ruleViolationResolutionService = ruleViolationResolutionService
            )

            provider.getResolutionsFor(
                RuleViolation(
                    rule = "rule",
                    pkg = null,
                    license = null,
                    licenseSources = enumSetOf(),
                    severity = Severity.WARNING,
                    message = literalMessage,
                    howToFix = ""
                )
            ) should containExactlyInAnyOrder(
                RuleViolationResolution(
                    message = literalMessage,
                    reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                    comment = "matching managed rule violation resolution"
                )
            )
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
                managedIssueResolutions = listOf(
                    ServerIssueResolution(
                        message = "match",
                        messageHash = calculateResolutionMessageHash("match"),
                        reason = ServerIssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "matching managed issue resolution",
                        source = ResolutionSource.SERVER
                    ),
                    ServerIssueResolution(
                        message = "no match",
                        messageHash = calculateResolutionMessageHash("no match"),
                        reason = ServerIssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "non-matching managed issue resolution",
                        source = ResolutionSource.SERVER
                    )
                ),
                managedRuleViolationResolutions = emptyList(),
                managedVulnerabilityResolutions = emptyList()
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

            val provider = OrtServerResolutionProvider(
                globalResolutions = globalResolutions,
                repositoryConfigurationResolutions = repositoryConfigurationResolutions,
                managedIssueResolutions = emptyList(),
                managedRuleViolationResolutions = listOf(
                    ServerRuleViolationResolution(
                        message = "match",
                        messageHash = calculateResolutionMessageHash("match"),
                        reason = ServerRuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "matching managed rule violation resolution",
                        source = ResolutionSource.SERVER
                    ),
                    ServerRuleViolationResolution(
                        message = "no match",
                        messageHash = calculateResolutionMessageHash("no match"),
                        reason = ServerRuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "non-matching managed rule violation resolution",
                        source = ResolutionSource.SERVER
                    )
                ),
                managedVulnerabilityResolutions = emptyList()
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
                RuleViolationResolution(
                    message = "match",
                    reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                    comment = "matching managed rule violation resolution"
                )
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
                managedIssueResolutions = emptyList(),
                managedRuleViolationResolutions = emptyList(),
                managedVulnerabilityResolutions = managedResolutions.vulnerabilities.map {
                    ServerVulnerabilityResolution(
                        externalId = it.id,
                        reason = ServerVulnerabilityResolutionReason.valueOf(it.reason.name),
                        comment = it.comment,
                        source = ResolutionSource.SERVER
                    )
                }
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

    "matchResolutions" should {
        "return matching issue resolutions" {
            val globalResolutions = Resolutions(
                issues = listOf(
                    IssueResolution(
                        message = "match-1",
                        reason = IssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "matching global issue resolution 1"
                    ),
                    IssueResolution(
                        message = "match-2",
                        reason = IssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "matching global issue resolution 2"
                    )
                )
            )

            val repositoryConfigurationResolutions = Resolutions(
                issues = listOf(
                    IssueResolution(
                        message = "match-1",
                        reason = IssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "matching repository configuration issue resolution 1"
                    ),
                    IssueResolution(
                        message = "match-2",
                        reason = IssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "matching repository configuration issue resolution 2"
                    )
                )
            )

            val provider = OrtServerResolutionProvider(
                globalResolutions = globalResolutions,
                repositoryConfigurationResolutions = repositoryConfigurationResolutions,
                managedIssueResolutions = listOf(
                    ServerIssueResolution(
                        message = "match-1",
                        messageHash = calculateResolutionMessageHash("match-1"),
                        reason = ServerIssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "matching managed issue resolution 1",
                        source = ResolutionSource.SERVER
                    ),
                    ServerIssueResolution(
                        message = "match-2",
                        messageHash = calculateResolutionMessageHash("match-2"),
                        reason = ServerIssueResolutionReason.CANT_FIX_ISSUE,
                        comment = "matching managed issue resolution 2",
                        source = ResolutionSource.SERVER
                    )
                ),
                managedRuleViolationResolutions = emptyList(),
                managedVulnerabilityResolutions = emptyList()
            )

            val issue1 = Issue(source = "source", message = "match-1")
            val issue2 = Issue(source = "source", message = "match-2")

            val expectedResolutionsForIssue1 = listOf(
                globalResolutions.issues[0].mapToModel(ResolutionSource.GLOBAL_FILE),
                repositoryConfigurationResolutions.issues[0].mapToModel(ResolutionSource.REPOSITORY_FILE),
                ServerIssueResolution(
                    message = "match-1",
                    messageHash = calculateResolutionMessageHash("match-1"),
                    reason = ServerIssueResolutionReason.CANT_FIX_ISSUE,
                    comment = "matching managed issue resolution 1",
                    source = ResolutionSource.SERVER
                )
            )

            val expectedResolutionsForIssue2 = listOf(
                globalResolutions.issues[1].mapToModel(ResolutionSource.GLOBAL_FILE),
                repositoryConfigurationResolutions.issues[1].mapToModel(ResolutionSource.REPOSITORY_FILE),
                ServerIssueResolution(
                    message = "match-2",
                    messageHash = calculateResolutionMessageHash("match-2"),
                    reason = ServerIssueResolutionReason.CANT_FIX_ISSUE,
                    comment = "matching managed issue resolution 2",
                    source = ResolutionSource.SERVER
                )
            )

            val matchResult = provider.matchResolutions(listOf(issue1, issue2), emptyList(), emptyList())

            matchResult.issues should containExactly(
                issue1.mapToModel() to expectedResolutionsForIssue1,
                issue2.mapToModel() to expectedResolutionsForIssue2
            )
        }

        "return matching rule violation resolutions" {
            val globalResolutions = Resolutions(
                ruleViolations = listOf(
                    RuleViolationResolution(
                        message = "match-1",
                        reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "matching global rule violation resolution 1"
                    ),
                    RuleViolationResolution(
                        message = "match-2",
                        reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "matching global rule violation resolution 2"
                    )
                )
            )

            val repositoryConfigurationResolutions = Resolutions(
                ruleViolations = listOf(
                    RuleViolationResolution(
                        message = "match-1",
                        reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "matching repository configuration rule violation resolution 1"
                    ),
                    RuleViolationResolution(
                        message = "match-2",
                        reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "matching repository configuration rule violation resolution 2"
                    )
                )
            )

            val provider = OrtServerResolutionProvider(
                globalResolutions = globalResolutions,
                repositoryConfigurationResolutions = repositoryConfigurationResolutions,
                managedIssueResolutions = emptyList(),
                managedRuleViolationResolutions = listOf(
                    ServerRuleViolationResolution(
                        message = "match-1",
                        messageHash = calculateResolutionMessageHash("match-1"),
                        reason = ServerRuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "matching managed rule violation resolution 1",
                        source = ResolutionSource.SERVER
                    ),
                    ServerRuleViolationResolution(
                        message = "match-2",
                        messageHash = calculateResolutionMessageHash("match-2"),
                        reason = ServerRuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "matching managed rule violation resolution 2",
                        source = ResolutionSource.SERVER
                    )
                ),
                managedVulnerabilityResolutions = emptyList()
            )

            val ruleViolation1 = RuleViolation(
                rule = "rule",
                pkg = null,
                license = null,
                licenseSources = enumSetOf(),
                severity = Severity.WARNING,
                message = "match-1",
                howToFix = ""
            )

            val ruleViolation2 = RuleViolation(
                rule = "rule",
                pkg = null,
                license = null,
                licenseSources = enumSetOf(),
                severity = Severity.WARNING,
                message = "match-2",
                howToFix = ""
            )

            val expectedResolutionsForRuleViolation1 = listOf(
                globalResolutions.ruleViolations[0].mapToModel(ResolutionSource.GLOBAL_FILE),
                repositoryConfigurationResolutions.ruleViolations[0].mapToModel(ResolutionSource.REPOSITORY_FILE),
                ServerRuleViolationResolution(
                    message = "match-1",
                    messageHash = calculateResolutionMessageHash("match-1"),
                    reason = ServerRuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                    comment = "matching managed rule violation resolution 1",
                    source = ResolutionSource.SERVER
                )
            )

            val expectedResolutionsForRuleViolation2 = listOf(
                globalResolutions.ruleViolations[1].mapToModel(ResolutionSource.GLOBAL_FILE),
                repositoryConfigurationResolutions.ruleViolations[1].mapToModel(ResolutionSource.REPOSITORY_FILE),
                ServerRuleViolationResolution(
                    message = "match-2",
                    messageHash = calculateResolutionMessageHash("match-2"),
                    reason = ServerRuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                    comment = "matching managed rule violation resolution 2",
                    source = ResolutionSource.SERVER
                )
            )

            val matchResult =
                provider.matchResolutions(emptyList(), listOf(ruleViolation1, ruleViolation2), emptyList())

            matchResult.ruleViolations should containExactly(
                ruleViolation1.mapToModel() to expectedResolutionsForRuleViolation1,
                ruleViolation2.mapToModel() to expectedResolutionsForRuleViolation2
            )
        }

        "return matching vulnerability resolutions" {
            val globalResolutions = Resolutions(
                vulnerabilities = listOf(
                    VulnerabilityResolution(
                        id = "match-1",
                        reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                        comment = "matching global vulnerability resolution 1"
                    ),
                    VulnerabilityResolution(
                        id = "match-2",
                        reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                        comment = "matching global vulnerability resolution 2"
                    )
                )
            )

            val repositoryConfigurationResolutions = Resolutions(
                vulnerabilities = listOf(
                    VulnerabilityResolution(
                        id = "match-1",
                        reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                        comment = "matching repository configuration vulnerability resolution 1"
                    ),
                    VulnerabilityResolution(
                        id = "match-2",
                        reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                        comment = "matching repository configuration vulnerability resolution 2"
                    )
                )
            )

            val managedResolutions = Resolutions(
                vulnerabilities = listOf(
                    VulnerabilityResolution(
                        id = "match-1",
                        reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                        comment = "matching managed vulnerability resolution 1"
                    ),
                    VulnerabilityResolution(
                        id = "match-2",
                        reason = VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                        comment = "matching managed vulnerability resolution 2"
                    )
                )
            )

            val provider = OrtServerResolutionProvider(
                globalResolutions = globalResolutions,
                repositoryConfigurationResolutions = repositoryConfigurationResolutions,
                managedIssueResolutions = emptyList(),
                managedRuleViolationResolutions = emptyList(),
                managedVulnerabilityResolutions = managedResolutions.vulnerabilities.map {
                    ServerVulnerabilityResolution(
                        externalId = it.id,
                        reason = ServerVulnerabilityResolutionReason.valueOf(it.reason.name),
                        comment = it.comment,
                        source = ResolutionSource.SERVER
                    )
                }
            )

            val vulnerability1 = Vulnerability(id = "match-1", references = emptyList())
            val vulnerability2 = Vulnerability(id = "match-2", references = emptyList())

            val expectedResolutionsForVulnerability1 = listOf(
                globalResolutions.vulnerabilities[0].mapToModel(ResolutionSource.GLOBAL_FILE),
                repositoryConfigurationResolutions.vulnerabilities[0].mapToModel(ResolutionSource.REPOSITORY_FILE),
                managedResolutions.vulnerabilities[0].mapToModel(ResolutionSource.SERVER)
            )

            val expectedResolutionsForVulnerability2 = listOf(
                globalResolutions.vulnerabilities[1].mapToModel(ResolutionSource.GLOBAL_FILE),
                repositoryConfigurationResolutions.vulnerabilities[1].mapToModel(ResolutionSource.REPOSITORY_FILE),
                managedResolutions.vulnerabilities[1].mapToModel(ResolutionSource.SERVER)
            )

            val matchResult =
                provider.matchResolutions(emptyList(), emptyList(), listOf(vulnerability1, vulnerability2))

            matchResult.vulnerabilities should containExactly(
                vulnerability1.mapToModel() to expectedResolutionsForVulnerability1,
                vulnerability2.mapToModel() to expectedResolutionsForVulnerability2
            )
        }

        "not include items without matching resolutions" {
            val provider = OrtServerResolutionProvider(
                globalResolutions = Resolutions(),
                repositoryConfigurationResolutions = Resolutions(),
                managedIssueResolutions = emptyList(),
                managedRuleViolationResolutions = emptyList(),
                managedVulnerabilityResolutions = emptyList()
            )

            val issue = Issue(source = "source", message = "no match")
            val ruleViolation = RuleViolation(
                rule = "rule",
                pkg = null,
                license = null,
                licenseSources = enumSetOf(),
                severity = Severity.WARNING,
                message = "no match",
                howToFix = ""
            )
            val vulnerability = Vulnerability(id = "no match", references = emptyList())

            val matchResult =
                provider.matchResolutions(listOf(issue), listOf(ruleViolation), listOf(vulnerability))

            matchResult.issues should beEmpty()
            matchResult.ruleViolations should beEmpty()
            matchResult.vulnerabilities should beEmpty()
        }
    }
})
