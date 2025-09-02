/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.secrets.routes.repository

import io.github.smiley4.ktoropenapi.get

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.components.secrets.Secret
import org.eclipse.apoapsis.ortserver.components.secrets.mapToApi
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.services.SecretService
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

internal fun Route.getSecretByRepositoryIdAndName(secretService: SecretService) =
    get("/repositories/{repositoryId}/secrets/{secretName}", {
        operationId = "GetSecretByRepositoryIdAndName"
        summary = "Get details of a secret of a repository"
        tags = listOf("Repositories")

        request {
            pathParameter<Long>("repositoryId") {
                description = "The repository's ID."
            }
            pathParameter<String>("secretName") {
                description = "The secret's name."
            }
        }

        response {
            HttpStatusCode.OK to {
                description = "Success"
                jsonBody<Secret> {
                    example("Get Secret") {
                        value = Secret(name = "token_npm_repo_1", description = "Access token for NPM Repo 1")
                    }
                }
            }
        }
    }) {
        requirePermission(RepositoryPermission.READ)

        val repositoryId = RepositoryId(call.requireIdParameter("repositoryId"))
        val secretName = call.requireParameter("secretName")

        secretService.getSecretByIdAndName(repositoryId, secretName)
            ?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
            ?: call.respond(HttpStatusCode.NotFound)
    }
