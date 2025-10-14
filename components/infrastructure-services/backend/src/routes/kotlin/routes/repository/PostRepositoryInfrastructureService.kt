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

import io.github.smiley4.ktoropenapi.post

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.requirePermission
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServiceService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.PostInfrastructureService
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToModel
import org.eclipse.apoapsis.ortserver.shared.apimodel.InfrastructureService
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter

internal fun Route.postRepositoryInfrastructureService(
    infrastructureServiceService: InfrastructureServiceService
) = post("/repositories/{repositoryId}/infrastructure-services", {
    operationId = "postRepositoryInfrastructureService"
    summary = "Create an infrastructure service for a repository"
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }
        jsonBody<PostInfrastructureService> {
            example("Create infrastructure service") {
                value = PostInfrastructureService(
                    name = "Artifactory",
                    url = "https://artifactory.example.org/releases",
                    description = "Artifactory repository",
                    usernameSecretRef = "artifactoryUsername",
                    passwordSecretRef = "artifactoryPassword"
                )
            }
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<InfrastructureService> {
                example("Create infrastructure service") {
                    value = InfrastructureService(
                        name = "Artifactory",
                        url = "https://artifactory.example.org/releases",
                        description = "Artifactory repository",
                        usernameSecretRef = "artifactoryUsername",
                        passwordSecretRef = "artifactoryPassword"
                    )
                }
            }
        }
    }
}) {
    requirePermission(RepositoryPermission.WRITE)

    val repositoryId = call.requireIdParameter("repositoryId")
    val createService = call.receive<PostInfrastructureService>()

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
