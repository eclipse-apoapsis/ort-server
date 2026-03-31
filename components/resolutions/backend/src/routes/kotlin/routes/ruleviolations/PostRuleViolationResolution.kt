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

import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.routes.OrtServerPrincipal.Companion.requirePrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.routes.post
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requirePermission
import org.eclipse.apoapsis.ortserver.components.resolutions.PostRuleViolationResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.ruleviolations.RuleViolationResolutionError
import org.eclipse.apoapsis.ortserver.components.resolutions.ruleviolations.RuleViolationResolutionService
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToModel
import org.eclipse.apoapsis.ortserver.shared.apimodel.RuleViolationResolutionReason
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter

internal fun Route.postRuleViolationResolution(
    ruleViolationResolutionService: RuleViolationResolutionService
) = post(
    "repositories/{repositoryId}/resolutions/rule-violations",
    {
        operationId = "postRuleViolationResolutionForRepository"
        summary = "Create a rule violation resolution for a repository."
        description = "Create a rule violation resolution for a repository."
        tags = listOf("Resolutions")

        request {
            pathParameter<String>("repositoryId") {
                description = "The ID of the repository the rule violation belongs to."
                required = true
            }

            jsonBody<PostRuleViolationResolution> {
                description = "The details of the rule violation resolution to create."
                required = true

                example("Example") {
                    value = PostRuleViolationResolution(
                        message = "A rule violation message.",
                        reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                        comment = "This rule violation is a known exception."
                    )
                }
            }
        }

        response {
            HttpStatusCode.Created to {
                description = "The rule violation resolution was successfully created."
            }

            HttpStatusCode.BadRequest to {
                description = "The request was invalid."
            }

            HttpStatusCode.NotFound to {
                description = "The repository or rule violation was not found."
            }
        }
    },
    requirePermission(RepositoryPermission.MANAGE_RESOLUTIONS)
) {
    val userId = requirePrincipal().userId
    val repositoryId = call.requireIdParameter("repositoryId")

    val postRuleViolationResolution = call.receive<PostRuleViolationResolution>()

    ruleViolationResolutionService.createResolution(
        repositoryId = RepositoryId(repositoryId),
        message = postRuleViolationResolution.message,
        createdBy = userId,
        reason = postRuleViolationResolution.reason.mapToModel(),
        comment = postRuleViolationResolution.comment
    ).onOk {
        call.respond(HttpStatusCode.Created)
    }.onErr {
        when (it) {
            is RuleViolationResolutionError.InvalidState -> call.respond(HttpStatusCode.BadRequest, it.message)
            is RuleViolationResolutionError.RepositoryNotFound -> call.respond(HttpStatusCode.NotFound, it.message)
            is RuleViolationResolutionError.ResolutionNotFound -> call.respond(HttpStatusCode.NotFound, it.message)
        }
    }
}
