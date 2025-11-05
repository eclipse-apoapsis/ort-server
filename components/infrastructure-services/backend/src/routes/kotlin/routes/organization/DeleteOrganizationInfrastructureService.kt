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

package org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.organization

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.routes.delete
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requirePermission
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServiceService
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

internal fun Route.deleteOrganizationInfrastructureService(
    infrastructureServiceService: InfrastructureServiceService
) = delete("/organizations/{organizationId}/infrastructure-services/{serviceName}", {
    operationId = "deleteOrganizationInfrastructureService"
    summary = "Delete an infrastructure service from an organization"
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
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
}, requirePermission(OrganizationPermission.WRITE)) {
    val orgId = call.requireIdParameter("organizationId")
    val serviceName = call.requireParameter("serviceName")

    infrastructureServiceService.deleteForId(OrganizationId(orgId), serviceName)

    call.respond(HttpStatusCode.NoContent)
}
