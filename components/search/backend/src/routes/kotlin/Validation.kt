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

package org.eclipse.apoapsis.ortserver.components.search.routes

import io.ktor.server.application.ApplicationCall

import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.HierarchyPermissions
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.routes.AuthorizationChecker
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId

/**
 * Parse the scope from query parameters. Returns the scope [HierarchyId] or null if no scope is provided.
 * Throws [QueryParametersException] if multiple scope parameters are provided.
 *
 * This function enforces that only one scope parameter (organizationId, productId, or repositoryId) can be
 * provided at a time to prevent authorization bypass attacks where a user could specify an organization they
 * have access to along with arbitrary product/repository IDs.
 */
fun ApplicationCall.parseScope(): HierarchyId? {
    val organizationIdParam = request.queryParameters["organizationId"]?.toLongOrNull()
    val productIdParam = request.queryParameters["productId"]?.toLongOrNull()
    val repositoryIdParam = request.queryParameters["repositoryId"]?.toLongOrNull()

    val scopeCount = listOfNotNull(organizationIdParam, productIdParam, repositoryIdParam).size

    if (scopeCount > 1) {
        throw QueryParametersException(
            "Only one scope parameter (organizationId, productId, or repositoryId) is allowed."
        )
    }

    return when {
        repositoryIdParam != null -> RepositoryId(repositoryIdParam)
        productIdParam != null -> ProductId(productIdParam)
        organizationIdParam != null -> OrganizationId(organizationIdParam)
        else -> null
    }
}

/**
 * Create an [AuthorizationChecker] that checks permissions dynamically based on the scope query parameters.
 * - If no scope is provided, requires superuser permission.
 * - If organizationId is provided, requires organization READ permission.
 * - If productId is provided, requires product READ permission.
 * - If repositoryId is provided, requires repository READ permission.
 *
 * Note: Only one scope parameter is allowed at a time to prevent authorization bypass attacks.
 * Use [parseScope] to extract and validate the scope from request parameters.
 */
fun requireScopedReadPermission(): AuthorizationChecker =
    object : AuthorizationChecker {
        override suspend fun loadEffectiveRole(
            service: AuthorizationService,
            userId: String,
            call: ApplicationCall
        ): EffectiveRole? {
            return when (val scope = call.parseScope()) {
                is RepositoryId -> {
                    service.checkPermissions(
                        userId,
                        scope,
                        HierarchyPermissions.permissions(RepositoryPermission.READ)
                    )
                }
                is ProductId -> {
                    service.checkPermissions(
                        userId,
                        scope,
                        HierarchyPermissions.permissions(ProductPermission.READ)
                    )
                }
                is OrganizationId -> {
                    service.checkPermissions(
                        userId,
                        scope,
                        HierarchyPermissions.permissions(OrganizationPermission.READ)
                    )
                }
                else -> {
                    // No scope provided, requires superuser
                    service.checkPermissions(
                        userId,
                        CompoundHierarchyId.WILDCARD,
                        HierarchyPermissions.permissions(OrganizationRole.ADMIN)
                    )?.takeIf { it.isSuperuser }
                }
            }
        }
    }
