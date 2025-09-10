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

package org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.repository

import io.github.smiley4.ktoropenapi.patch

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.UpdateInfrastructureService
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.services.InfrastructureServiceService
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToModel
import org.eclipse.apoapsis.ortserver.shared.apimodel.CredentialsType
import org.eclipse.apoapsis.ortserver.shared.apimodel.InfrastructureService
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

internal fun Route.patchInfrastructureServiceForRepositoryIdAndName(
    infrastructureServiceService: InfrastructureServiceService
) = patch("/repositories/{repositoryId}/infrastructure-services/{serviceName}", {
    operationId = "PatchInfrastructureServiceForRepositoryIdAndName"
    summary = "Update an infrastructure service for a repository"
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }
        pathParameter<String>("serviceName") {
            description = "The name of the infrastructure service."
        }
        jsonBody<UpdateInfrastructureService> {
            example("Update infrastructure service") {
                value = UpdateInfrastructureService(
                    url = "https://github.com".asPresent(),
                    description = "Updated description".asPresent(),
                    usernameSecretRef = "newGitHubUser".asPresent(),
                    passwordSecretRef = "newGitHubPassword".asPresent(),
                    credentialsTypes = setOf(CredentialsType.NETRC_FILE).asPresent()
                )
            }
            description = "Set the values that should be updated. To delete a value, set it explicitly to null."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<InfrastructureService> {
                example("Update infrastructure service") {
                    value = InfrastructureService(
                        name = "GitHub",
                        url = "https://github.com",
                        description = "Updated description",
                        usernameSecretRef = "newGitHubUser",
                        passwordSecretRef = "newGitHubPassword",
                        credentialsTypes = setOf(CredentialsType.NETRC_FILE)
                    )
                }
            }
        }
    }
}) {
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
