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

package org.eclipse.apoapsis.ortserver.components.authorization.routes

import io.ktor.server.application.ApplicationCall

import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.HierarchyPermissions
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter

/**
 * An interface defining a mechanism to check for required permissions using an [AuthorizationService] instance.
 *
 * The idea behind this interface is that a concrete implementation is responsible for doing a concrete authorization
 * check, such as testing for the presence of a specific permission on an element of the hierarchy. To do this, the
 * instance needs to load the permissions on this element from the service and then test whether the affected
 * permission is contained.
 *
 * Implementations of this interface can be passed into special routing functions that use them to perform
 * authorization checks automatically. There are convenience functions to create default instances easily.
 */
interface AuthorizationChecker {
    /**
     * Use the provided [service] to load the [EffectiveRole] of the user with the given [userId] for the current
     * [call]. A typical implementation will figure out the ID of an element in the hierarchy (organization, product,
     * or repository) based on current call parameters. Then it can invoke the [service] to query the permissions on
     * this element. If the permission check is successful, return a properly initialized [EffectiveRole] instance.
     * Otherwise, return *null*, which cause the request to fail with a 403 error.
     */
    suspend fun loadEffectiveRole(service: AuthorizationService, userId: String, call: ApplicationCall): EffectiveRole?
}

/** The name of the request parameter referring to the organization ID. */
private const val ORGANIZATION_ID_PARAM = "organizationId"

/** The name of the request parameter referring to the product ID. */
private const val PRODUCT_ID_PARAM = "productId"

/** The name of the request parameter referring to the repository ID. */
private const val REPOSITORY_ID_PARAM = "repositoryId"

/**
 * Create an [AuthorizationChecker] that checks for the presence of the given organization-level [permission].
 */
fun requirePermission(permission: OrganizationPermission): AuthorizationChecker =
    object : AuthorizationChecker {
        override suspend fun loadEffectiveRole(
            service: AuthorizationService,
            userId: String,
            call: ApplicationCall
        ): EffectiveRole? =
            service.checkPermissions(
                userId,
                OrganizationId(call.requireIdParameter(ORGANIZATION_ID_PARAM)),
                HierarchyPermissions.permissions(permission)
            )
    }

/**
 * Create an [AuthorizationChecker] that checks for the presence of the given product-level [permission].
 */
fun requirePermission(permission: ProductPermission): AuthorizationChecker =
    object : AuthorizationChecker {
        override suspend fun loadEffectiveRole(
            service: AuthorizationService,
            userId: String,
            call: ApplicationCall
        ): EffectiveRole? =
            service.checkPermissions(
                userId,
                ProductId(call.requireIdParameter(PRODUCT_ID_PARAM)),
                HierarchyPermissions.permissions(permission)
            )
    }

/**
 * Create an [AuthorizationChecker] that checks for the presence of the given repository-level [permission].
 */
fun requirePermission(permission: RepositoryPermission): AuthorizationChecker =
    object : AuthorizationChecker {
        override suspend fun loadEffectiveRole(
            service: AuthorizationService,
            userId: String,
            call: ApplicationCall
        ): EffectiveRole? =
            service.checkPermissions(
                userId,
                RepositoryId(call.requireIdParameter(REPOSITORY_ID_PARAM)),
                HierarchyPermissions.permissions(permission)
            )
    }

/**
 * Create an [AuthorizationChecker] that checks whether the user is a superuser.
 */
fun requireSuperuser(): AuthorizationChecker =
    object : AuthorizationChecker {
        override suspend fun loadEffectiveRole(
            service: AuthorizationService,
            userId: String,
            call: ApplicationCall
        ): EffectiveRole? =
            service.checkPermissions(
                userId,
                CompoundHierarchyId.WILDCARD,
                HierarchyPermissions.permissions(OrganizationRole.ADMIN)
            )?.takeIf { it.isSuperuser }
    }
