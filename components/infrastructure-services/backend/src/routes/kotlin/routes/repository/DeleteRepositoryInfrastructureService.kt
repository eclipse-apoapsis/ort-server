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

import io.github.smiley4.ktoropenapi.delete

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.requirePermission
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServiceService
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

internal fun Route.deleteRepositoryInfrastructureService(
    infrastructureServiceService: InfrastructureServiceService
) = delete("/repositories/{repositoryId}/infrastructure-services/{serviceName}", {
    operationId = "deleteRepositoryInfrastructureService"
    summary = "Delete an infrastructure service from a repository"
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }
        pathParameter<String>("serviceName") {
            description = "The name of the infrastructure service."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Success"
        }
    }
}) {
    requirePermission(RepositoryPermission.WRITE)

    val repositoryId = call.requireIdParameter("repositoryId")
    val serviceName = call.requireParameter("serviceName")

    infrastructureServiceService.deleteForId(RepositoryId(repositoryId), serviceName)

    call.respond(HttpStatusCode.NoContent)
}
