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

package org.eclipse.apoapsis.ortserver.components.resolutions.routes.ruleviolations

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.resolutions.PostRuleViolationResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.beOk
import org.eclipse.apoapsis.ortserver.components.resolutions.routes.ResolutionsIntegrationTest
import org.eclipse.apoapsis.ortserver.components.resolutions.shouldBeOk
import org.eclipse.apoapsis.ortserver.dao.utils.calculateResolutionMessageHash
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.runs.repository.ResolutionSource
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolutionReason
import org.eclipse.apoapsis.ortserver.shared.apimodel.RuleViolationResolutionReason as ApiRuleViolationResolutionReason

class PostRuleViolationResolutionIntegrationTest : ResolutionsIntegrationTest({
    var repositoryId = RepositoryId(-1)

    beforeEach {
        repositoryId = RepositoryId(dbExtension.fixtures.repository.id)
    }

    "PostRuleViolationResolution" should {
        "create a rule violation resolution for a repository" {
            resolutionsTestApplication { client ->
                val response = client.post("/repositories/${repositoryId.value}/resolutions/rule-violations") {
                    setBody(
                        PostRuleViolationResolution(
                            message = "A rule violation message.",
                            comment = "This rule violation is a known exception.",
                            reason = ApiRuleViolationResolutionReason.CANT_FIX_EXCEPTION
                        )
                    )
                }

                response shouldHaveStatus HttpStatusCode.Created

                ruleViolationResolutionService.getResolutionsForRepository(repositoryId).shouldBeOk {
                    it should containExactly(
                        RuleViolationResolution(
                            message = "A rule violation message.",
                            messageHash = calculateResolutionMessageHash("A rule violation message."),
                            comment = "This rule violation is a known exception.",
                            reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                            source = ResolutionSource.SERVER
                        )
                    )
                }
            }
        }

        "fail if the repository does not exist" {
            resolutionsTestApplication { client ->
                val response = client.post("/repositories/9999/resolutions/rule-violations") {
                    setBody(
                        PostRuleViolationResolution(
                            message = "A rule violation message.",
                            comment = "This rule violation is a known exception.",
                            reason = ApiRuleViolationResolutionReason.CANT_FIX_EXCEPTION
                        )
                    )
                }

                response shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "fail if there is already a resolution for the rule violation" {
            resolutionsTestApplication { client ->
                ruleViolationResolutionService.createResolution(
                    repositoryId = repositoryId,
                    message = "A rule violation message.",
                    reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                    comment = "This rule violation is a known exception.",
                    createdBy = "test-user"
                ) should beOk()

                val response = client.post("/repositories/${repositoryId.value}/resolutions/rule-violations") {
                    setBody(
                        PostRuleViolationResolution(
                            message = "A rule violation message.",
                            comment = "This rule violation is a known exception.",
                            reason = ApiRuleViolationResolutionReason.CANT_FIX_EXCEPTION
                        )
                    )
                }

                response shouldHaveStatus HttpStatusCode.BadRequest
            }
        }
    }
})
