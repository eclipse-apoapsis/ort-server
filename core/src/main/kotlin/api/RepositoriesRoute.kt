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
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateInfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateInfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.components.authorization.OrtPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.getFullName
import org.eclipse.apoapsis.ortserver.components.authorization.getUserId
import org.eclipse.apoapsis.ortserver.components.authorization.getUsername
import org.eclipse.apoapsis.ortserver.components.authorization.hasRole
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.components.authorization.roles.Superuser
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.mapToApi
import org.eclipse.apoapsis.ortserver.core.api.UserWithGroupsHelper.sortAndPage
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteInfrastructureServiceForRepositoryIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteOrtRunByIndex
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteRepositoryById
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteUserFromRepositoryGroup
import org.eclipse.apoapsis.ortserver.core.apiDocs.getInfrastructureServicesByRepositoryId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrtRunByIndex
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrtRunsByRepositoryId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getRepositoryById
import org.eclipse.apoapsis.ortserver.core.apiDocs.getUsersForRepository
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchInfrastructureServiceForRepositoryIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchRepositoryById
import org.eclipse.apoapsis.ortserver.core.apiDocs.postInfrastructureServiceForRepository
import org.eclipse.apoapsis.ortserver.core.apiDocs.postOrtRun
import org.eclipse.apoapsis.ortserver.core.apiDocs.putUserToRepositoryGroup
import org.eclipse.apoapsis.ortserver.core.services.OrchestratorService
import org.eclipse.apoapsis.ortserver.core.utils.getUnavailablePlugins
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.UserDisplayName
import org.eclipse.apoapsis.ortserver.services.InfrastructureServiceService
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.services.UserService
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToModel
import org.eclipse.apoapsis.ortserver.shared.apimodel.ErrorResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.ktorutils.pagingOptions
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

import org.koin.ktor.ext.inject

@Suppress("LongMethod")
fun Route.repositories() = route("repositories/{repositoryId}") {
    val orchestratorService by inject<OrchestratorService>()
    val ortRunService by inject<OrtRunService>()
    val pluginService by inject<PluginService>()
    val repositoryService by inject<RepositoryService>()
    val userService by inject<UserService>()
    val infrastructureServiceService by inject<InfrastructureServiceService>()

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

                // Check if unavailable plugins are used.
                val unavailablePlugins = createOrtRun.getUnavailablePlugins(pluginService)
                if (unavailablePlugins.isNotEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            message = "Unavailable plugins are used.",
                            cause = unavailablePlugins.errorMessage()
                        )
                    )
                    return@post
                }

                // Restrict some job configuration options to superusers only.
                if (!hasRole(Superuser.ROLE_NAME)) {
                    // Keep alive worker configuration might be directly set in the worker's job configuration
                    forbidIfTrue("advisor.keepAliveWorker", createOrtRun.jobConfigs.advisor?.keepAliveWorker)
                    forbidIfTrue("analyzer.keepAliveWorker", createOrtRun.jobConfigs.analyzer.keepAliveWorker)
                    forbidIfTrue("evaluator.keepAliveWorker", createOrtRun.jobConfigs.evaluator?.keepAliveWorker)
                    forbidIfTrue("notifier.keepAliveWorker", createOrtRun.jobConfigs.notifier?.keepAliveWorker)
                    forbidIfTrue("reporter.keepAliveWorker", createOrtRun.jobConfigs.reporter?.keepAliveWorker)
                    forbidIfTrue("scanner.keepAliveWorker", createOrtRun.jobConfigs.scanner?.keepAliveWorker)

                    // Keep alive worker configuration might also be set in the general configuration parameters
                    createOrtRun.jobConfigs.parameters?.entries
                        ?.find { entry -> entry.key.endsWith("keepalive", ignoreCase = true) && entry.value == "true" }
                        ?.let { entry -> throw ForbiddenConfigurationPropertyException(entry.key) }
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

    route("infrastructure-services") {
        get(getInfrastructureServicesByRepositoryId) {
            requirePermission(RepositoryPermission.READ)

            val repositoryId = call.requireIdParameter("repositoryId")
            val pagingOptions = call.pagingOptions(SortProperty("name", SortDirection.ASCENDING))

            val infrastructureServices =
                infrastructureServiceService.listForId(RepositoryId(repositoryId), pagingOptions.mapToModel())

            val pagedResponse = infrastructureServices.mapToApi(InfrastructureService::mapToApi)

            call.respond(HttpStatusCode.OK, pagedResponse)
        }

        post(postInfrastructureServiceForRepository) {
            requirePermission(RepositoryPermission.WRITE)

            val repositoryId = call.requireIdParameter("repositoryId")
            val createService = call.receive<CreateInfrastructureService>()

            val newService = infrastructureServiceService.createForId(
                RepositoryId(repositoryId),
                createService.name,
                createService.url,
                createService.description,
                createService.usernameSecretRef,
                createService.passwordSecretRef,
                createService.credentialsTypes.mapToModel()
            )

            call.respond(HttpStatusCode.Created, newService.mapToApi())
        }

        route("{serviceName}") {
            patch(patchInfrastructureServiceForRepositoryIdAndName) {
                requirePermission(RepositoryPermission.WRITE)

                val repositoryId = call.requireIdParameter("repositoryId")
                val serviceName = call.requireParameter("serviceName")
                val updateService = call.receive<UpdateInfrastructureService>()

                val updatedService = infrastructureServiceService.updateForId(
                    RepositoryId(repositoryId),
                    serviceName,
                    updateService.url.mapToModel(),
                    updateService.description.mapToModel(),
                    updateService.usernameSecretRef.mapToModel(),
                    updateService.passwordSecretRef.mapToModel(),
                    updateService.credentialsTypes.mapToModel { type -> type.mapToModel() }
                )

                call.respond(HttpStatusCode.OK, updatedService.mapToApi())
            }

            delete(deleteInfrastructureServiceForRepositoryIdAndName) {
                requirePermission(RepositoryPermission.WRITE)

                val repositoryId = call.requireIdParameter("repositoryId")
                val serviceName = call.requireParameter("serviceName")

                infrastructureServiceService.deleteForId(RepositoryId(repositoryId), serviceName)

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    route("groups") {
        // Instead of identifying arbitrary groups with a groupId, there are only 3 groups with fixed
        // groupId "readers", "writers" or "admins".
        route("{groupId}") {
            put(putUserToRepositoryGroup) {
                requirePermission(RepositoryPermission.MANAGE_GROUPS)

                val user = call.receive<Username>()
                val repositoryId = call.requireIdParameter("repositoryId")
                val groupId = call.requireParameter("groupId")

                repositoryService.addUserToGroup(user.username, repositoryId, groupId)
                call.respond(HttpStatusCode.NoContent)
            }

            delete(deleteUserFromRepositoryGroup) {
                requirePermission(RepositoryPermission.MANAGE_GROUPS)

                val repositoryId = call.requireIdParameter("repositoryId")
                val groupId = call.requireParameter("groupId")
                val username = call.requireParameter("username")

                repositoryService.removeUserFromGroup(username, repositoryId, groupId)
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

private fun forbidIfTrue(name: String, value: Boolean?) {
    if (value == true) throw ForbiddenConfigurationPropertyException(name)
}
