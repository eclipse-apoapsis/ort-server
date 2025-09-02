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

package org.eclipse.apoapsis.ortserver.components.secrets.routes.organization

import io.github.smiley4.ktoropenapi.delete

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.services.SecretService
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

internal fun Route.deleteSecretByOrganizationIdAndName(secretService: SecretService) =
    delete("/organizations/{organizationId}/secrets/{secretName}", {
        operationId = "DeleteSecretByOrganizationIdAndName"
        summary = "Delete a secret from an organization"
        tags = listOf("Organizations")

        request {
            pathParameter<Long>("organizationId") {
                description = "The organization's ID."
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
        requirePermission(OrganizationPermission.WRITE_SECRETS)

        val organizationId = OrganizationId(call.requireIdParameter("organizationId"))
        val secretName = call.requireParameter("secretName")

        secretService.deleteSecretByIdAndName(organizationId, secretName)

        call.respond(HttpStatusCode.NoContent)
    }
