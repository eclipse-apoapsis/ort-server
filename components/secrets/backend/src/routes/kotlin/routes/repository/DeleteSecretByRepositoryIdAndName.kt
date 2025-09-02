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

import io.github.smiley4.ktoropenapi.delete

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.services.SecretService
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

internal fun Route.deleteSecretByRepositoryIdAndName(secretService: SecretService) =
    delete("/repositories/{repositoryId}/secrets/{secretName}", {
        operationId = "DeleteSecretByRepositoryIdAndName"
        summary = "Delete a secret from a repository"
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
            HttpStatusCode.NoContent to {
                description = "Success"
            }
        }
    }) {
        requirePermission(RepositoryPermission.WRITE_SECRETS)

        val repositoryId = RepositoryId(call.requireIdParameter("repositoryId"))
        val secretName = call.requireParameter("secretName")

        secretService.deleteSecretByIdAndName(repositoryId, secretName)

        call.respond(HttpStatusCode.NoContent)
    }
