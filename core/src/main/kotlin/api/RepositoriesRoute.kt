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

import io.github.smiley4.ktorswaggerui.dsl.delete
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.patch
import io.github.smiley4.ktorswaggerui.dsl.post

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.PagedResponse
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.model.mapToApiSummary
import org.eclipse.apoapsis.ortserver.api.v1.model.mapToModel
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteRepositoryById
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteSecretByRepositoryIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrtRunByIndex
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrtRuns
import org.eclipse.apoapsis.ortserver.core.apiDocs.getRepositoryById
import org.eclipse.apoapsis.ortserver.core.apiDocs.getSecretByRepositoryIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.getSecretsByRepositoryId
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchRepositoryById
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchSecretByRepositoryIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.postOrtRun
import org.eclipse.apoapsis.ortserver.core.apiDocs.postSecretForRepository
import org.eclipse.apoapsis.ortserver.core.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.core.services.OrchestratorService
import org.eclipse.apoapsis.ortserver.core.utils.pagingOptions
import org.eclipse.apoapsis.ortserver.core.utils.requireParameter
import org.eclipse.apoapsis.ortserver.model.authorization.RepositoryPermission
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.services.SecretService

import org.koin.ktor.ext.inject

fun Route.repositories() = route("repositories/{repositoryId}") {
    val orchestratorService by inject<OrchestratorService>()
    val repositoryService by inject<RepositoryService>()
    val secretService by inject<SecretService>()

    get(getRepositoryById) {
        requirePermission(RepositoryPermission.READ)

        val id = call.requireParameter("repositoryId").toLong()

        repositoryService.getRepository(id)?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
            ?: call.respond(HttpStatusCode.NotFound)
    }

    patch(patchRepositoryById) {
        requirePermission(RepositoryPermission.WRITE)

        val id = call.requireParameter("repositoryId").toLong()
        val updateRepository = call.receive<UpdateRepository>()

        val updatedRepository = repositoryService.updateRepository(
            id,
            updateRepository.type.mapToModel { it.mapToModel() },
            updateRepository.url.mapToModel()
        )

        call.respond(HttpStatusCode.OK, updatedRepository.mapToApi())
    }

    delete(deleteRepositoryById) {
        requirePermission(RepositoryPermission.DELETE)

        val id = call.requireParameter("repositoryId").toLong()

        repositoryService.deleteRepository(id)

        call.respond(HttpStatusCode.NoContent)
    }

    route("runs") {
        get(getOrtRuns) {
            requirePermission(RepositoryPermission.READ_ORT_RUNS)

            val repositoryId = call.requireParameter("repositoryId").toLong()
            val pagingOptions = call.pagingOptions(SortProperty("index", SortDirection.ASCENDING))

            val jobsForOrtRuns = repositoryService.getOrtRuns(repositoryId, pagingOptions.mapToModel())
                .map { it.mapToApiSummary(repositoryService.getJobs(repositoryId, it.index)!!.mapToApiSummary()) }
            val pagedResponse = PagedResponse(
                jobsForOrtRuns,
                pagingOptions
            )

            call.respond(HttpStatusCode.OK, pagedResponse)
        }

        post(postOrtRun) {
            requirePermission(RepositoryPermission.TRIGGER_ORT_RUN)

            val repositoryId = call.requireParameter("repositoryId").toLong()
            val createOrtRun = call.receive<CreateOrtRun>()

            call.respond(
                HttpStatusCode.Created,
                orchestratorService.createOrtRun(
                    repositoryId,
                    createOrtRun.revision,
                    createOrtRun.jobConfigs.mapToModel(),
                    createOrtRun.jobConfigContext,
                    createOrtRun.labels
                ).mapToApi(Jobs())
            )
        }

        route("{ortRunIndex}") {
            get(getOrtRunByIndex) {
                requirePermission(RepositoryPermission.READ_ORT_RUNS)

                val repositoryId = call.requireParameter("repositoryId").toLong()
                val ortRunIndex = call.requireParameter("ortRunIndex").toLong()

                repositoryService.getOrtRun(repositoryId, ortRunIndex)
                    ?.let {
                        call.respond(
                            HttpStatusCode.OK,
                            it.mapToApi(repositoryService.getJobs(repositoryId, ortRunIndex)!!.mapToApi())
                        )
                    }
                    ?: call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    route("secrets") {
        get(getSecretsByRepositoryId) {
            requirePermission(RepositoryPermission.READ)

            val repositoryId = call.requireParameter("repositoryId").toLong()
            val pagingOptions = call.pagingOptions(SortProperty("name", SortDirection.ASCENDING))

            val secretsForRepository = secretService.listForRepository(repositoryId, pagingOptions.mapToModel())
            val pagedResponse = PagedResponse(
                secretsForRepository.map { it.mapToApi() },
                pagingOptions
            )

            call.respond(HttpStatusCode.OK, pagedResponse)
        }

        route("{secretName}") {
            get(getSecretByRepositoryIdAndName) {
                requirePermission(RepositoryPermission.READ)

                val repositoryId = call.requireParameter("repositoryId").toLong()
                val secretName = call.requireParameter("secretName")

                secretService.getSecretByRepositoryIdAndName(repositoryId, secretName)
                    ?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
                    ?: call.respond(HttpStatusCode.NotFound)
            }

            patch(patchSecretByRepositoryIdAndName) {
                requirePermission(RepositoryPermission.WRITE_SECRETS)

                val repositoryId = call.requireParameter("repositoryId").toLong()
                val secretName = call.requireParameter("secretName")
                val updateSecret = call.receive<UpdateSecret>()

                call.respond(
                    HttpStatusCode.OK,
                    secretService.updateSecretByRepositoryAndName(
                        repositoryId,
                        secretName,
                        updateSecret.value.mapToModel(),
                        updateSecret.description.mapToModel()
                    ).mapToApi()
                )
            }

            delete(deleteSecretByRepositoryIdAndName) {
                requirePermission(RepositoryPermission.WRITE_SECRETS)

                val repositoryId = call.requireParameter("repositoryId").toLong()
                val secretName = call.requireParameter("secretName")

                secretService.deleteSecretByRepositoryAndName(repositoryId, secretName)

                call.respond(HttpStatusCode.NoContent)
            }
        }

        post(postSecretForRepository) {
            requirePermission(RepositoryPermission.WRITE_SECRETS)

            val repositoryId = call.requireParameter("repositoryId").toLong()
            val createSecret = call.receive<CreateSecret>()

            call.respond(
                HttpStatusCode.Created,
                secretService.createSecret(
                    createSecret.name,
                    createSecret.value,
                    createSecret.description,
                    null,
                    null,
                    repositoryId
                ).mapToApi()
            )
        }
    }
}
