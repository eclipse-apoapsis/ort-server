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

package org.eclipse.apoapsis.ortserver.components.authorization.routes.userinfo

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole
import org.eclipse.apoapsis.ortserver.components.authorization.routes.AuthorizationChecker
import org.eclipse.apoapsis.ortserver.components.authorization.routes.OrtServerPrincipal.Companion.requirePrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId

internal fun Route.getSuperuser() = get("/authorization/superuser", {
    operationId = "getSuperuser"
    summary = "Check if the current user is a superuser"
    tags = listOf("Authorization")

    response {
        HttpStatusCode.OK to {
            description = "Success"
            body<Boolean> {
                description = "Whether the current user is a superuser."
            }
        }
    }
}, superuserChecker()) {
    val isSuperuser = requirePrincipal().effectiveRole.isSuperuser
    call.respond(HttpStatusCode.OK, isSuperuser.toString())
}

/**
 * Return a special [AuthorizationChecker] that only loads the effective role for the current user to have access to
 * the superuser status.
 */
private fun superuserChecker(): AuthorizationChecker =
    object : AuthorizationChecker {
        override suspend fun loadEffectiveRole(
            service: AuthorizationService,
            userId: String,
            call: ApplicationCall
        ): EffectiveRole =
            service.getEffectiveRole(userId, CompoundHierarchyId.WILDCARD)
    }
