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

import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.resolutions.PatchIssueResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.PatchRuleViolationResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.PatchVulnerabilityResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.PostIssueResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.PostRuleViolationResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.PostVulnerabilityResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.issues.IssueResolutionEventStore
import org.eclipse.apoapsis.ortserver.components.resolutions.issues.IssueResolutionService
import org.eclipse.apoapsis.ortserver.components.resolutions.ruleviolations.RuleViolationResolutionEventStore
import org.eclipse.apoapsis.ortserver.components.resolutions.ruleviolations.RuleViolationResolutionService
import org.eclipse.apoapsis.ortserver.components.resolutions.vulnerabilities.VulnerabilityResolutionEventStore
import org.eclipse.apoapsis.ortserver.components.resolutions.vulnerabilities.VulnerabilityResolutionService
import org.eclipse.apoapsis.ortserver.dao.utils.calculateResolutionMessageHash
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.shared.apimodel.IssueResolutionReason
import org.eclipse.apoapsis.ortserver.shared.apimodel.RuleViolationResolutionReason
import org.eclipse.apoapsis.ortserver.shared.apimodel.VulnerabilityResolutionReason
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

class ResolutionsAuthorizationTest : AbstractAuthorizationTest({
    lateinit var issueResolutionService: IssueResolutionService
    lateinit var ruleViolationResolutionService: RuleViolationResolutionService
    lateinit var vulnerabilityResolutionService: VulnerabilityResolutionService
    lateinit var hierarchyId: CompoundHierarchyId
    var repositoryId = RepositoryId(-1)

    beforeEach {
        val repositoryService = RepositoryService(
            db = dbExtension.db,
            ortRunRepository = dbExtension.fixtures.ortRunRepository,
            repositoryRepository = dbExtension.fixtures.repositoryRepository,
            analyzerJobRepository = dbExtension.fixtures.analyzerJobRepository,
            advisorJobRepository = dbExtension.fixtures.advisorJobRepository,
            scannerJobRepository = dbExtension.fixtures.scannerJobRepository,
            evaluatorJobRepository = dbExtension.fixtures.evaluatorJobRepository,
            reporterJobRepository = dbExtension.fixtures.reporterJobRepository,
            notifierJobRepository = dbExtension.fixtures.notifierJobRepository,
            authorizationService = mockk()
        )

        val issueResolutionEventStore = IssueResolutionEventStore(dbExtension.db)

        issueResolutionService = IssueResolutionService(
            db = dbExtension.db,
            eventStore = issueResolutionEventStore,
            repositoryService = repositoryService
        )
        ruleViolationResolutionService = RuleViolationResolutionService(
            db = dbExtension.db,
            eventStore = RuleViolationResolutionEventStore(dbExtension.db),
            repositoryService = repositoryService
        )
        vulnerabilityResolutionService = VulnerabilityResolutionService(
            db = dbExtension.db,
            eventStore = VulnerabilityResolutionEventStore(dbExtension.db),
            repositoryService = repositoryService
        )

        repositoryId = RepositoryId(dbExtension.fixtures.repository.id)

        hierarchyId = CompoundHierarchyId.forRepository(
            OrganizationId(dbExtension.fixtures.organization.id),
            ProductId(dbExtension.fixtures.product.id),
            repositoryId
        )
    }

    "PostVulnerabilityResolution" should {
        "require RepositoryPermission.MANAGE_RESOLUTIONS" {
            requestShouldRequireRole(
                routes = {
                    resolutionRoutes(
                        issueResolutionService,
                        ruleViolationResolutionService,
                        vulnerabilityResolutionService
                    )
                },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.Created,
                hierarchyId = hierarchyId
            ) {
                post("/repositories/${repositoryId.value}/resolutions/vulnerabilities/CVE-2021-1234") {
                    setBody(
                        PostVulnerabilityResolution(
                            comment = "This is not a vulnerability.",
                            reason = VulnerabilityResolutionReason.NOT_A_VULNERABILITY
                        )
                    )
                }
            }
        }
    }

    "DeleteVulnerabilityResolution" should {
        "require RepositoryPermission.MANAGE_RESOLUTIONS" {
            requestShouldRequireRole(
                routes = {
                    resolutionRoutes(
                        issueResolutionService,
                        ruleViolationResolutionService,
                        vulnerabilityResolutionService
                    )
                },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = hierarchyId
            ) {
                delete("/repositories/${repositoryId.value}/resolutions/vulnerabilities/CVE-2021-1234")
            }
        }
    }

    "PatchVulnerabilityResolution" should {
        "require RepositoryPermission.MANAGE_RESOLUTIONS" {
            requestShouldRequireRole(
                routes = {
                    resolutionRoutes(
                        issueResolutionService,
                        ruleViolationResolutionService,
                        vulnerabilityResolutionService
                    )
                },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = hierarchyId
            ) {
                patch("/repositories/${repositoryId.value}/resolutions/vulnerabilities/CVE-2021-1234") {
                    setBody(
                        PatchVulnerabilityResolution(
                            comment = "This is not a vulnerability.",
                            reason = VulnerabilityResolutionReason.NOT_A_VULNERABILITY
                        )
                    )
                }
            }
        }
    }

    "PostIssueResolution" should {
        "require RepositoryPermission.MANAGE_RESOLUTIONS" {
            requestShouldRequireRole(
                routes = {
                    resolutionRoutes(
                        issueResolutionService,
                        ruleViolationResolutionService,
                        vulnerabilityResolutionService
                    )
                },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.Created,
                hierarchyId = hierarchyId
            ) {
                post("/repositories/${repositoryId.value}/resolutions/issues") {
                    setBody(
                        PostIssueResolution(
                            message = "scanner-issue",
                            comment = "This scanner issue is a known false positive.",
                            reason = IssueResolutionReason.SCANNER_ISSUE
                        )
                    )
                }
            }
        }
    }

    "DeleteIssueResolution" should {
        "require RepositoryPermission.MANAGE_RESOLUTIONS" {
            requestShouldRequireRole(
                routes = {
                    resolutionRoutes(
                        issueResolutionService,
                        ruleViolationResolutionService,
                        vulnerabilityResolutionService
                    )
                },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = hierarchyId
            ) {
                delete(
                    "/repositories/${repositoryId.value}/resolutions/issues/" +
                            calculateResolutionMessageHash("scanner-issue")
                )
            }
        }
    }

    "PatchIssueResolution" should {
        "require RepositoryPermission.MANAGE_RESOLUTIONS" {
            requestShouldRequireRole(
                routes = {
                    resolutionRoutes(
                        issueResolutionService,
                        ruleViolationResolutionService,
                        vulnerabilityResolutionService
                    )
                },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = hierarchyId
            ) {
                patch(
                    "/repositories/${repositoryId.value}/resolutions/issues/" +
                            calculateResolutionMessageHash("scanner-issue")
                ) {
                    setBody(
                        PatchIssueResolution(
                            comment = "This issue is caused by an upstream build tool defect.",
                            reason = IssueResolutionReason.BUILD_TOOL_ISSUE
                        )
                    )
                }
            }
        }
    }

    "PostRuleViolationResolution" should {
        "require RepositoryPermission.MANAGE_RESOLUTIONS" {
            requestShouldRequireRole(
                routes = {
                    resolutionRoutes(
                        issueResolutionService,
                        ruleViolationResolutionService,
                        vulnerabilityResolutionService
                    )
                },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.Created,
                hierarchyId = hierarchyId
            ) {
                post("/repositories/${repositoryId.value}/resolutions/rule-violations") {
                    setBody(
                        PostRuleViolationResolution(
                            message = "A rule violation message.",
                            comment = "This rule violation is a known exception.",
                            reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION
                        )
                    )
                }
            }
        }
    }

    "DeleteRuleViolationResolution" should {
        "require RepositoryPermission.MANAGE_RESOLUTIONS" {
            requestShouldRequireRole(
                routes = {
                    resolutionRoutes(
                        issueResolutionService,
                        ruleViolationResolutionService,
                        vulnerabilityResolutionService
                    )
                },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = hierarchyId
            ) {
                delete(
                    "/repositories/${repositoryId.value}/resolutions/rule-violations/" +
                            calculateResolutionMessageHash("A rule violation message.")
                )
            }
        }
    }

    "PatchRuleViolationResolution" should {
        "require RepositoryPermission.MANAGE_RESOLUTIONS" {
            requestShouldRequireRole(
                routes = {
                    resolutionRoutes(
                        issueResolutionService,
                        ruleViolationResolutionService,
                        vulnerabilityResolutionService
                    )
                },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = hierarchyId
            ) {
                patch(
                    "/repositories/${repositoryId.value}/resolutions/rule-violations/" +
                            calculateResolutionMessageHash("A rule violation message.")
                ) {
                    setBody(
                        PatchRuleViolationResolution(
                            comment = "This rule violation is accepted because of dynamic linking.",
                            reason = RuleViolationResolutionReason.DYNAMIC_LINKAGE_EXCEPTION
                        )
                    )
                }
            }
        }
    }
})
