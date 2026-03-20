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

import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.routes.OrtServerPrincipal.Companion.requirePrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.routes.patch
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requirePermission
import org.eclipse.apoapsis.ortserver.components.resolutions.PatchIssueResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.issues.IssueResolutionError
import org.eclipse.apoapsis.ortserver.components.resolutions.issues.IssueResolutionService
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToModel
import org.eclipse.apoapsis.ortserver.shared.apimodel.IssueResolutionReason
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

internal fun Route.patchIssueResolution(
    issueResolutionService: IssueResolutionService
) = patch(
    "repositories/{repositoryId}/resolutions/issues/{messageHash}",
    {
        operationId = "patchIssueResolutionForRepository"
        summary = "Update an issue resolution for an issue in a repository."
        description = "Update an issue resolution for an issue in a repository."
        tags = listOf("Resolutions")

        request {
            pathParameter<String>("repositoryId") {
                description = "The ID of the repository the issue belongs to."
                required = true
            }

            pathParameter<String>("messageHash") {
                description = "The stable technical identifier of the issue resolution to update."
                required = true
            }

            jsonBody<PatchIssueResolution> {
                description = "The details of the issue resolution to update."
                required = true

                example("Example") {
                    value = PatchIssueResolution(
                        reason = IssueResolutionReason.BUILD_TOOL_ISSUE,
                        comment = "This build tool issue is caused by an upstream bug."
                    )
                }
            }
        }

        response {
            HttpStatusCode.OK to {
                description = "The issue resolution was updated successfully."
            }

            HttpStatusCode.BadRequest to {
                description = "The request was invalid."
            }

            HttpStatusCode.NotFound to {
                description = "The repository or issue resolution was not found."
            }
        }
    },
    requirePermission(RepositoryPermission.MANAGE_RESOLUTIONS)
) {
    val userId = requirePrincipal().userId
    val repositoryId = call.requireIdParameter("repositoryId")
    val messageHash = call.requireParameter("messageHash")

    val patchIssueResolution = call.receive<PatchIssueResolution>()

    issueResolutionService.updateResolutionByHash(
        repositoryId = RepositoryId(repositoryId),
        messageHash = messageHash,
        updatedBy = userId,
        reason = patchIssueResolution.reason?.mapToModel(),
        comment = patchIssueResolution.comment
    ).onOk {
        call.respond(HttpStatusCode.OK)
    }.onErr {
        when (it) {
            is IssueResolutionError.InvalidState -> call.respond(HttpStatusCode.BadRequest, it.message)
            is IssueResolutionError.RepositoryNotFound -> call.respond(HttpStatusCode.NotFound, it.message)
            is IssueResolutionError.ResolutionNotFound -> call.respond(HttpStatusCode.NotFound, it.message)
        }
    }
}
