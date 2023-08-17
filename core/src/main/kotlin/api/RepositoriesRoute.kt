/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.api

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

import org.koin.ktor.ext.inject

import org.ossreviewtoolkit.server.api.v1.CreateOrtRun
import org.ossreviewtoolkit.server.api.v1.CreateSecret
import org.ossreviewtoolkit.server.api.v1.Jobs
import org.ossreviewtoolkit.server.api.v1.UpdateRepository
import org.ossreviewtoolkit.server.api.v1.UpdateSecret
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.api.v1.mapToModel
import org.ossreviewtoolkit.server.core.apiDocs.deleteRepositoryById
import org.ossreviewtoolkit.server.core.apiDocs.deleteSecretByRepositoryIdAndName
import org.ossreviewtoolkit.server.core.apiDocs.getOrtRunByIndex
import org.ossreviewtoolkit.server.core.apiDocs.getOrtRuns
import org.ossreviewtoolkit.server.core.apiDocs.getRepositoryById
import org.ossreviewtoolkit.server.core.apiDocs.getSecretByRepositoryIdAndName
import org.ossreviewtoolkit.server.core.apiDocs.getSecretsByRepositoryId
import org.ossreviewtoolkit.server.core.apiDocs.patchRepositoryById
import org.ossreviewtoolkit.server.core.apiDocs.patchSecretByRepositoryIdAndName
import org.ossreviewtoolkit.server.core.apiDocs.postOrtRun
import org.ossreviewtoolkit.server.core.apiDocs.postSecretForRepository
import org.ossreviewtoolkit.server.core.authorization.requirePermission
import org.ossreviewtoolkit.server.core.services.OrchestratorService
import org.ossreviewtoolkit.server.core.utils.listQueryParameters
import org.ossreviewtoolkit.server.core.utils.requireParameter
import org.ossreviewtoolkit.server.model.authorization.RepositoryPermission
import org.ossreviewtoolkit.server.services.RepositoryService
import org.ossreviewtoolkit.server.services.SecretService

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

        val updatedRepository =
            repositoryService.updateRepository(id, updateRepository.type.mapToModel(), updateRepository.url)

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

            val ortRuns = repositoryService.getOrtRuns(repositoryId, call.listQueryParameters())
            call.respond(
                HttpStatusCode.OK,
                ortRuns.map { it.mapToApi(repositoryService.getJobs(repositoryId, it.index)!!.mapToApi()) }
            )
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

            call.respond(
                HttpStatusCode.OK,
                secretService.listForRepository(repositoryId, call.listQueryParameters()).map { it.mapToApi() }
            )
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
                        updateSecret.value,
                        updateSecret.description
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
