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

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToModel
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.components.authorization.OrtPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.api.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.getFullName
import org.eclipse.apoapsis.ortserver.components.authorization.getUserId
import org.eclipse.apoapsis.ortserver.components.authorization.getUsername
import org.eclipse.apoapsis.ortserver.components.authorization.hasRole
import org.eclipse.apoapsis.ortserver.components.authorization.mapToModel
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.components.authorization.roles.Superuser
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.mapToApi
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.sortAndPage
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteOrtRunByIndex
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteRepositoryById
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteRepositoryRoleFromUser
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrtRunByIndex
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrtRunsByRepositoryId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getRepositoryById
import org.eclipse.apoapsis.ortserver.core.apiDocs.getUsersForRepository
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchRepositoryById
import org.eclipse.apoapsis.ortserver.core.apiDocs.postOrtRun
import org.eclipse.apoapsis.ortserver.core.apiDocs.putRepositoryRoleToUser
import org.eclipse.apoapsis.ortserver.core.services.OrchestratorService
import org.eclipse.apoapsis.ortserver.core.utils.getPluginConfigs
import org.eclipse.apoapsis.ortserver.core.utils.hasKeepAliveWorkerFlag
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.UserDisplayName
import org.eclipse.apoapsis.ortserver.services.AuthorizationService
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.services.UserService
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

    get(getRepositoryById) {
        requirePermission(RepositoryPermission.READ)

        val id = call.requireIdParameter("repositoryId")

        repositoryService.getRepository(id)?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
            ?: call.respond(HttpStatusCode.NotFound)
    }

    patch(patchRepositoryById) {
        requirePermission(RepositoryPermission.WRITE)

        val id = call.requireIdParameter("repositoryId")
        val updateRepository = call.receive<UpdateRepository>()

        val updatedRepository = repositoryService.updateRepository(
            id,
            updateRepository.type.mapToModel { it.mapToModel() },
            updateRepository.url.mapToModel(),
            updateRepository.description.mapToModel()
        )

        call.respond(HttpStatusCode.OK, updatedRepository.mapToApi())
    }

    delete(deleteRepositoryById) {
        requirePermission(RepositoryPermission.DELETE)

        val id = call.requireIdParameter("repositoryId")

        repositoryService.deleteRepository(id)

        call.respond(HttpStatusCode.NoContent)
    }

    route("runs") {
        get(getOrtRunsByRepositoryId) {
            requirePermission(RepositoryPermission.READ_ORT_RUNS)

            val repositoryId = call.requireIdParameter("repositoryId")
            val pagingOptions = call.pagingOptions(SortProperty("index", SortDirection.ASCENDING))

            val ortRunSummaries = repositoryService.getOrtRunSummaries(repositoryId, pagingOptions.mapToModel())
            val pagedResponse = ortRunSummaries.mapToApi { it.mapToApi() }
            call.respond(HttpStatusCode.OK, pagedResponse)
        }

        post(postOrtRun) {
            requirePermission(RepositoryPermission.TRIGGER_ORT_RUN)

            val repositoryId = call.requireIdParameter("repositoryId")

            repositoryService.getRepository(repositoryId)?.let {
                val createOrtRun = call.receive<CreateOrtRun>()

                // Extract the user information from the principal.
                val userDisplayName = call.principal<OrtPrincipal>()?.let { principal ->
                    UserDisplayName(principal.getUserId(), principal.getUsername(), principal.getFullName())
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
                if (createOrtRun.hasKeepAliveWorkerFlag() && !hasRole(Superuser.ROLE_NAME)) {
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
            get(getOrtRunByIndex) {
                requirePermission(RepositoryPermission.READ_ORT_RUNS)

                val repositoryId = call.requireIdParameter("repositoryId")
                val ortRunIndex = call.requireIdParameter("ortRunIndex")

                repositoryService.getOrtRun(repositoryId, ortRunIndex)?.let {
                    repositoryService.getJobs(repositoryId, ortRunIndex)?.let { jobs ->
                        call.respond(HttpStatusCode.OK, it.mapToApi(jobs.mapToApi()))
                    }
                } ?: call.respond(HttpStatusCode.NotFound)
            }

            delete(deleteOrtRunByIndex) {
                val repositoryId = call.requireIdParameter("repositoryId")
                val ortRunIndex = call.requireIdParameter("ortRunIndex")

                requirePermission(RepositoryPermission.DELETE)

                repositoryService.getOrtRunId(repositoryId, ortRunIndex)?.let { ortRunId ->
                    ortRunService.deleteOrtRun(ortRunId)
                    call.respond(HttpStatusCode.NoContent)
                } ?: call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    route("roles") {
        route("{role}") {
            put(putRepositoryRoleToUser) {
                requirePermission(RepositoryPermission.MANAGE_GROUPS)

                val user = call.receive<Username>()
                val repositoryId = call.requireIdParameter("repositoryId")
                val role = call.requireEnumParameter<RepositoryRole>("role").mapToModel()

                if (repositoryService.getRepository(repositoryId) == null) {
                    call.respondError(HttpStatusCode.NotFound, "Repository with ID '$repositoryId' not found.")
                    return@put
                }

                authorizationService.addUserRole(user.username, RepositoryId(repositoryId), role)
                call.respond(HttpStatusCode.NoContent)
            }

            delete(deleteRepositoryRoleFromUser) {
                requirePermission(RepositoryPermission.MANAGE_GROUPS)

                val repositoryId = call.requireIdParameter("repositoryId")
                val role = call.requireEnumParameter<RepositoryRole>("role").mapToModel()
                val username = call.requireParameter("username")

                if (repositoryService.getRepository(repositoryId) == null) {
                    call.respondError(HttpStatusCode.NotFound, "Repository with ID '$repositoryId' not found.")
                    return@delete
                }

                authorizationService.removeUserRole(username, RepositoryId(repositoryId), role)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    route("users") {
        get(getUsersForRepository) {
            requirePermission(RepositoryPermission.READ)

            val repositoryId = call.requireIdParameter("repositoryId")
            val pagingOptions = call.pagingOptions(SortProperty("username", SortDirection.ASCENDING))

            val users = userService.getUsersHavingRightsForRepository(repositoryId).mapToApi()

            call.respond(
                PagedResponse(users.sortAndPage(pagingOptions), pagingOptions.toPagingData(users.size.toLong()))
            )
        }
    }
}
