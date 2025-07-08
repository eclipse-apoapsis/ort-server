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

package org.eclipse.apoapsis.ortserver.components.authorization

import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingContext

import org.eclipse.apoapsis.ortserver.components.authorization.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.roles.Superuser
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter

/**
 * Return `true` if the [OrtPrincipal] of the current [call] has the provided [permission] for the organization with the
 * provided [organizationId].
 */
fun RoutingContext.hasPermission(
    organizationId: Long,
    permission: OrganizationPermission
): Boolean = hasRole(permission.roleName(organizationId))

/**
 * Return `true` if the [OrtPrincipal] of the current [call] has the provided [role].
 */
fun RoutingContext.hasRole(role: String): Boolean {
    val principal = call.principal<OrtPrincipal>()
    return principal.isSuperuser() || principal.hasRole(role)
}

/**
 * Require that the [OrtPrincipal] of the current[call] has the provided [permission]. Throw an [AuthorizationException]
 * otherwise.
 */
fun RoutingContext.requirePermission(permission: OrganizationPermission) {
    val orgId = call.requireIdParameter("organizationId")
    requirePermission(permission.roleName(orgId))
}

/**
 * Require that the [OrtPrincipal] of the current[call] has the provided [permission]. Throw an [AuthorizationException]
 * otherwise.
 */
fun RoutingContext.requirePermission(permission: ProductPermission) {
    val productId = call.requireIdParameter("productId")
    requirePermission(permission.roleName(productId))
}

/**
 * Require that the [OrtPrincipal] of the current[call] has the provided [permission]. Throw an [AuthorizationException]
 * otherwise.
 */
fun RoutingContext.requirePermission(permission: RepositoryPermission) {
    val repositoryId = call.requireIdParameter("repositoryId")
    requirePermission(permission.roleName(repositoryId))
}

/**
 * Require that the [OrtPrincipal] of the current [call] has the [requiredRole]. Throw an [AuthorizationException]
 * otherwise.
 */
fun RoutingContext.requirePermission(requiredRole: String) {
    if (!hasRole(requiredRole)) {
        throw AuthorizationException()
    }
}

/**
 * Require that the [OrtPrincipal] of the current [call] is a [Superuser]. Throw an [AuthorizationException] otherwise.
 */
fun RoutingContext.requireSuperuser() {
    requirePermission(Superuser.ROLE_NAME)
}

/**
 * Require that the [OrtPrincipal] of the current [call] is authenticated. Throw an [AuthorizationException] otherwise.
 */
fun RoutingContext.requireAuthenticated() {
    if (call.principal<OrtPrincipal>() == null) {
        throw AuthorizationException()
    }
}
