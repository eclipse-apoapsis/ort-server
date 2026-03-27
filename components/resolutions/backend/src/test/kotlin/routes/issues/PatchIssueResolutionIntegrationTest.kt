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

package org.eclipse.apoapsis.ortserver.components.resolutions.routes.issues

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should

import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.resolutions.PatchIssueResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.beOk
import org.eclipse.apoapsis.ortserver.components.resolutions.routes.ResolutionsIntegrationTest
import org.eclipse.apoapsis.ortserver.components.resolutions.shouldBeOk
import org.eclipse.apoapsis.ortserver.dao.utils.calculateResolutionMessageHash
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolutionReason
import org.eclipse.apoapsis.ortserver.model.runs.repository.ResolutionSource
import org.eclipse.apoapsis.ortserver.shared.apimodel.IssueResolutionReason as ApiIssueResolutionReason

class PatchIssueResolutionIntegrationTest : ResolutionsIntegrationTest({
    var repositoryId = RepositoryId(-1)

    beforeEach {
        repositoryId = RepositoryId(dbExtension.fixtures.repository.id)
    }

    "PatchIssueResolution" should {
        "update an issue resolution for a repository" {
            resolutionsTestApplication { client ->
                issueResolutionService.createResolution(
                    repositoryId = repositoryId,
                    message = "scanner-issue",
                    reason = IssueResolutionReason.SCANNER_ISSUE,
                    comment = "This scanner issue is a known false positive.",
                    createdBy = "test-user"
                ) should beOk()

                val response = client.patch(
                    "/repositories/${repositoryId.value}/resolutions/issues/" +
                            calculateResolutionMessageHash("scanner-issue")
                ) {
                    setBody(
                        PatchIssueResolution(
                            comment = "This issue is caused by an upstream build tool defect.",
                            reason = ApiIssueResolutionReason.BUILD_TOOL_ISSUE
                        )
                    )
                }

                response shouldHaveStatus HttpStatusCode.OK

                issueResolutionService.getResolutionsForRepository(repositoryId).shouldBeOk {
                    it should containExactly(
                        IssueResolution(
                            message = "scanner-issue",
                            messageHash = calculateResolutionMessageHash("scanner-issue"),
                            comment = "This issue is caused by an upstream build tool defect.",
                            reason = IssueResolutionReason.BUILD_TOOL_ISSUE,
                            source = ResolutionSource.SERVER
                        )
                    )
                }
            }
        }

        "fail if the repository does not exist" {
            resolutionsTestApplication { client ->
                val response = client.patch(
                    "/repositories/9999/resolutions/issues/" +
                            calculateResolutionMessageHash("scanner-issue")
                ) {
                    setBody(
                        PatchIssueResolution(
                            comment = "This issue is caused by an upstream build tool defect.",
                            reason = ApiIssueResolutionReason.BUILD_TOOL_ISSUE
                        )
                    )
                }

                response shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "fail if the issue resolution does not exist" {
            resolutionsTestApplication { client ->
                val response = client.patch(
                    "/repositories/${repositoryId.value}/resolutions/issues/missing-issue-hash"
                ) {
                    setBody(
                        PatchIssueResolution(
                            comment = "This issue is caused by an upstream build tool defect.",
                            reason = ApiIssueResolutionReason.BUILD_TOOL_ISSUE
                        )
                    )
                }

                response shouldHaveStatus HttpStatusCode.NotFound
            }
        }
    }
})
