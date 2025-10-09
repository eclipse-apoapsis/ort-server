/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
import org.eclipse.apoapsis.ortserver.components.secrets.SecretService
import org.eclipse.apoapsis.ortserver.components.secrets.mapToApi
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.standardListQueryParameters

internal fun Route.getAvailableRepositorySecrets(
    repositoryService: RepositoryService,
    secretService: SecretService
) = get("/repositories/{repositoryId}/availableSecrets", {
    operationId = "getAvailableRepositorySecrets"
    summary = "Get all available secrets for a repository"
    description = "Get all secrets that are available in the context of the provided repository. In addition to " +
            "the repository secrets, this includes secrets from the product and organization the repository " +
            "belongs to."
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The ID of the repository."
        }
        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<List<Secret>> {
                example("Get all available secrets for a repository") {
                    value = listOf(
                        Secret(name = "USERNAME-SECRET", description = "The username."),
                        Secret(name = "PASSWORD-SECRET", description = "The password.")
                    )
                }
            }
        }
    }
}) {
    requirePermission(RepositoryPermission.READ)

    val repositoryId = call.requireIdParameter("repositoryId")

    val hierarchy = repositoryService.getHierarchy(repositoryId)
    val secrets = secretService.listForHierarchy(hierarchy)

    call.respond(HttpStatusCode.OK, secrets.map { it.mapToApi() })
}
