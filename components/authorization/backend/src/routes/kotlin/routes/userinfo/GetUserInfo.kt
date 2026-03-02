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

import org.eclipse.apoapsis.ortserver.components.authorization.api.OrganizationPermission as ApiOrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.api.ProductPermission as ApiProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.api.RepositoryPermission as ApiRepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.api.UserInfo
import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.routes.AuthorizationChecker
import org.eclipse.apoapsis.ortserver.components.authorization.routes.OrtServerPrincipal.Companion.requirePrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.authorization.routes.mapToApi
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyLevel
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody

internal fun Route.getUserInfo() = get("/authorization/userinfo", {
    operationId = "getUserInfo"
    summary = "Return information about the current user including the permissions on the current hierarchy level."
    tags = listOf("Authorization")

    request {
        queryParameter<Long>("organizationId") {
            description = "The ID of the organization if querying information on organization level."
            required = false
        }
        queryParameter<Long>("productId") {
            description = "The ID of the product if querying information on product level."
            required = false
        }
        queryParameter<Long>("repositoryId") {
            description = "The ID of the repository if querying information on repository level."
            required = false
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<UserInfo> {
                description = "An object with information about the current user."
                example("Get user info") {
                    value = UserInfo(
                        username = "jdoe",
                        fullName = "John Doe",
                        isSuperuser = false,
                        repositoryPermissions = setOf(
                            ApiRepositoryPermission.READ,
                            ApiRepositoryPermission.READ_ORT_RUNS,
                            ApiRepositoryPermission.WRITE,
                            ApiRepositoryPermission.TRIGGER_ORT_RUN
                        )
                    )
                }
            }
        }
        HttpStatusCode.BadRequest to {
            description = "If more than one query parameter determining the hierarchy is specified."
        }
    }
}, userInfoChecker()) {
    if (listOf(ORGANIZATION_PARAM, PRODUCT_PARAM, REPOSITORY_PARAM).mapNotNull { call.queryParameters[it] }.size > 1) {
        call.respond(
            HttpStatusCode.BadRequest,
            "Only one parameter for the hierarchy may be provided."
        )
        return@get
    }

    val principal = requirePrincipal()
    val permissions = extractPermissions(principal.effectiveRole)

    val userInfo = UserInfo(
        username = principal.username,
        fullName = principal.fullName.orEmpty(),
        organizationPermissions = permissions.organizationPermissions,
        productPermissions = permissions.productPermissions,
        repositoryPermissions = permissions.repositoryPermissions,
        isSuperuser = principal.effectiveRole.isSuperuser
    )
    call.respond(HttpStatusCode.OK, userInfo)
}

/**
 * Return a [Set] with the names of all permissions contained in the given [effectiveRole] for the current hierarchy
 * level. If the current user is a superuser, the set contains all permissions of the ADMIN role on this level.
 * For requests without any scope given, empty sets are returned.
 */
private fun extractPermissions(effectiveRole: EffectiveRole): UserInfoPermissions =
    when (effectiveRole.elementId.level) {
        HierarchyLevel.ORGANIZATION -> UserInfoPermissions(
            organizationPermissions = effectiveRole.organizationPermissionsForLevel()
                .mapTo(mutableSetOf()) { it.mapToApi() }
        )

        HierarchyLevel.PRODUCT -> UserInfoPermissions(
            productPermissions = effectiveRole.productPermissionsForLevel()
                .mapTo(mutableSetOf()) { it.mapToApi() }
        )

        HierarchyLevel.REPOSITORY -> UserInfoPermissions(
            repositoryPermissions = effectiveRole.repositoryPermissionsForLevel()
                .mapTo(mutableSetOf()) { it.mapToApi() }
        )

        HierarchyLevel.WILDCARD -> UserInfoPermissions()
    }

private fun EffectiveRole.organizationPermissionsForLevel() =
    if (isSuperuser) OrganizationRole.ADMIN.organizationPermissions else getOrganizationPermissions()

private fun EffectiveRole.productPermissionsForLevel() =
    if (isSuperuser) ProductRole.ADMIN.productPermissions else getProductPermissions()

private fun EffectiveRole.repositoryPermissionsForLevel() =
    if (isSuperuser) RepositoryRole.ADMIN.repositoryPermissions else getRepositoryPermissions()

private data class UserInfoPermissions(
    val organizationPermissions: Set<ApiOrganizationPermission> = emptySet(),
    val productPermissions: Set<ApiProductPermission> = emptySet(),
    val repositoryPermissions: Set<ApiRepositoryPermission> = emptySet()
)

/** The query parameter for the organization ID. */
private const val ORGANIZATION_PARAM = "organizationId"

/** The query parameter for the product ID. */
private const val PRODUCT_PARAM = "productId"

/** The query parameter for the repository ID. */
private const val REPOSITORY_PARAM = "repositoryId"

/**
 * A special [AuthorizationChecker] implementation that makes sure that the [EffectiveRole] for the correct hierarchy
 * level is loaded based on the provided query parameters.
 */
private fun userInfoChecker(): AuthorizationChecker =
    object : AuthorizationChecker {
        override suspend fun loadEffectiveRole(
            service: AuthorizationService,
            userId: String,
            call: ApplicationCall
        ): EffectiveRole {
            val hierarchyId = call.request.queryParameters[REPOSITORY_PARAM]?.let { RepositoryId(it.toLong()) }
                ?: call.request.queryParameters[PRODUCT_PARAM]?.let { ProductId(it.toLong()) }
                ?: call.request.queryParameters[ORGANIZATION_PARAM]?.let { OrganizationId(it.toLong()) }

            return hierarchyId?.let { id -> service.getEffectiveRole(userId, id) }
                ?: service.getEffectiveRole(userId, CompoundHierarchyId.WILDCARD)
        }
    }
