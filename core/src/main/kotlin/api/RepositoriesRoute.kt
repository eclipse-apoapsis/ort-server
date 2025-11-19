/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToModel
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.PatchRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.PostRepositoryRun
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.components.authorization.api.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.routes.delete
import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.authorization.routes.mapToModel
import org.eclipse.apoapsis.ortserver.components.authorization.routes.ortServerPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.routes.patch
import org.eclipse.apoapsis.ortserver.components.authorization.routes.post
import org.eclipse.apoapsis.ortserver.components.authorization.routes.put
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requirePermission
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.components.authorization.service.UserService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.mapToApi
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.sortAndPage
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteRepository
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteRepositoryRoleFromUser
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteRepositoryRun
import org.eclipse.apoapsis.ortserver.core.apiDocs.getRepository
import org.eclipse.apoapsis.ortserver.core.apiDocs.getRepositoryRun
import org.eclipse.apoapsis.ortserver.core.apiDocs.getRepositoryRuns
import org.eclipse.apoapsis.ortserver.core.apiDocs.getRepositoryUsers
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchRepository
import org.eclipse.apoapsis.ortserver.core.apiDocs.postRepositoryRun
import org.eclipse.apoapsis.ortserver.core.apiDocs.putRepositoryRoleToUser
import org.eclipse.apoapsis.ortserver.core.services.OrchestratorService
import org.eclipse.apoapsis.ortserver.core.utils.getPluginConfigs
import org.eclipse.apoapsis.ortserver.core.utils.hasKeepAliveWorkerFlag
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.UserDisplayName
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToModel
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.ktorutils.pagingOptions
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireEnumParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.respondError

import org.koin.ktor.ext.inject

@Suppress("LongMethod")
fun Route.repositories() = route("repositories/{repositoryId}") {
    val authorizationService by inject<AuthorizationService>()
    val orchestratorService by inject<OrchestratorService>()
    val ortRunService by inject<OrtRunService>()
    val pluginTemplateService by inject<PluginTemplateService>()
    val repositoryService by inject<RepositoryService>()
    val userService by inject<UserService>()

    get(getRepository, requirePermission(RepositoryPermission.READ)) {
        val id = call.requireIdParameter("repositoryId")

        repositoryService.getRepository(id)?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
            ?: call.respond(HttpStatusCode.NotFound)
    }

    patch(patchRepository, requirePermission(RepositoryPermission.WRITE)) {
        val id = call.requireIdParameter("repositoryId")
        val updateRepository = call.receive<PatchRepository>()

        val updatedRepository = repositoryService.updateRepository(
            id,
            updateRepository.type.mapToModel { it.mapToModel() },
            updateRepository.url.mapToModel(),
            updateRepository.description.mapToModel()
        )

        call.respond(HttpStatusCode.OK, updatedRepository.mapToApi())
    }

    delete(deleteRepository, requirePermission(RepositoryPermission.DELETE)) {
        val id = call.requireIdParameter("repositoryId")

        repositoryService.deleteRepository(id)

        call.respond(HttpStatusCode.NoContent)
    }

    route("runs") {
        get(getRepositoryRuns, requirePermission(RepositoryPermission.READ_ORT_RUNS)) {
            val repositoryId = call.requireIdParameter("repositoryId")
            val pagingOptions = call.pagingOptions(SortProperty("index", SortDirection.ASCENDING))

            val ortRunSummaries = repositoryService.getOrtRunSummaries(repositoryId, pagingOptions.mapToModel())
            val pagedResponse = ortRunSummaries.mapToApi { it.mapToApi() }
            call.respond(HttpStatusCode.OK, pagedResponse)
        }

        post(postRepositoryRun, requirePermission(RepositoryPermission.TRIGGER_ORT_RUN)) {
            val repositoryId = call.requireIdParameter("repositoryId")

            repositoryService.getRepository(repositoryId)?.let {
                val createOrtRun = call.receive<PostRepositoryRun>()

                // Extract the user information from the principal.
                val userDisplayName = call.ortServerPrincipal.let { principal ->
                    UserDisplayName(principal.userId, principal.username, principal.fullName)
                }

                // Validate the plugin configuration.
                val validationResult = pluginTemplateService.validatePluginConfigs(
                    pluginConfigs = createOrtRun.getPluginConfigs().mapValues { (_, pluginConfigs) ->
                        pluginConfigs.mapValues { (_, pluginConfig) -> pluginConfig.mapToModel() }
                    },
                    organizationId = it.organizationId
                )

                if (!validationResult.isValid) {
                    call.respondError(
                        HttpStatusCode.BadRequest,
                        message = "Invalid plugin configuration.",
                        cause = validationResult.errors.joinToString(separator = "\n")
                    )
                    return@post
                }

                // Restrict the `keepAliveWorker` flags to superusers only.
                if (createOrtRun.hasKeepAliveWorkerFlag() && !call.ortServerPrincipal.effectiveRole.isSuperuser) {
                    call.respondError(
                        HttpStatusCode.Forbidden,
                        "The 'keepAliveWorker' flag is only allowed for superusers."
                    )
                    return@post
                }

                call.respond(
                    HttpStatusCode.Created,
                    orchestratorService.createOrtRun(
                        repositoryId,
                        createOrtRun.revision,
                        createOrtRun.path,
                        createOrtRun.jobConfigs.mapToModel(),
                        createOrtRun.jobConfigContext,
                        createOrtRun.labels,
                        createOrtRun.environmentConfigPath,
                        userDisplayName
                    ).mapToApi(Jobs())
                )
            } ?: call.respond(HttpStatusCode.NotFound)
        }

        route("{ortRunIndex}") {
            get(getRepositoryRun, requirePermission(RepositoryPermission.READ_ORT_RUNS)) {
                val repositoryId = call.requireIdParameter("repositoryId")
                val ortRunIndex = call.requireIdParameter("ortRunIndex")

                repositoryService.getOrtRun(repositoryId, ortRunIndex)?.let {
                    repositoryService.getJobs(repositoryId, ortRunIndex)?.let { jobs ->
                        call.respond(HttpStatusCode.OK, it.mapToApi(jobs.mapToApi()))
                    }
                } ?: call.respond(HttpStatusCode.NotFound)
            }

            delete(deleteRepositoryRun, requirePermission(RepositoryPermission.DELETE)) {
                val repositoryId = call.requireIdParameter("repositoryId")
                val ortRunIndex = call.requireIdParameter("ortRunIndex")

                repositoryService.getOrtRunId(repositoryId, ortRunIndex)?.let { ortRunId ->
                    ortRunService.deleteOrtRun(ortRunId)
                    call.respond(HttpStatusCode.NoContent)
                } ?: call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    route("roles") {
        route("{role}") {
            put(putRepositoryRoleToUser, requirePermission(RepositoryPermission.MANAGE_GROUPS)) {
                val user = call.receive<Username>()
                val repositoryId = call.requireIdParameter("repositoryId")
                val role = call.requireEnumParameter<RepositoryRole>("role").mapToModel()

                if (repositoryService.getRepository(repositoryId) == null) {
                    call.respondError(HttpStatusCode.NotFound, "Repository with ID '$repositoryId' not found.")
                    return@put
                }

                if (!userService.userExists(user.username)) {
                    call.respondError(HttpStatusCode.NotFound, "Could not find user '${user.username}'.")
                    return@put
                }

                authorizationService.assignRole(user.username, role, call.ortServerPrincipal.effectiveRole.elementId)
                call.respond(HttpStatusCode.NoContent)
            }

            delete(deleteRepositoryRoleFromUser, requirePermission(RepositoryPermission.MANAGE_GROUPS)) {
                val repositoryId = call.requireIdParameter("repositoryId")
                call.requireEnumParameter<RepositoryRole>("role")
                val username = call.requireParameter("username")

                if (repositoryService.getRepository(repositoryId) == null) {
                    call.respondError(HttpStatusCode.NotFound, "Repository with ID '$repositoryId' not found.")
                    return@delete
                }

                if (!userService.userExists(username)) {
                    call.respondError(HttpStatusCode.NotFound, "Could not find user '$username'.")
                    return@delete
                }

                authorizationService.removeAssignment(username, call.ortServerPrincipal.effectiveRole.elementId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    route("users") {
        get(getRepositoryUsers, requirePermission(RepositoryPermission.READ)) {
            val pagingOptions = call.pagingOptions(SortProperty("username", SortDirection.ASCENDING))

            val users = authorizationService.listUsers(call.ortServerPrincipal.effectiveRole.elementId)
                .mapToApi(userService) { it.assignedAt.level == CompoundHierarchyId.REPOSITORY_LEVEL }

            call.respond(
                PagedResponse(users.sortAndPage(pagingOptions), pagingOptions.toPagingData(users.size.toLong()))
            )
        }
    }
}
