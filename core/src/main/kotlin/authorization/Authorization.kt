/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.authorization

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.util.pipeline.PipelineContext

import org.ossreviewtoolkit.server.core.api.AuthorizationException
import org.ossreviewtoolkit.server.core.utils.requireParameter
import org.ossreviewtoolkit.server.model.authorization.OrganizationPermission
import org.ossreviewtoolkit.server.model.authorization.ProductPermission
import org.ossreviewtoolkit.server.model.authorization.RepositoryPermission
import org.ossreviewtoolkit.server.model.authorization.Superuser

/**
 * Require that the [OrtPrincipal] of the current[call] has the provided [permission]. Throw an [AuthorizationException]
 * otherwise.
 */
fun PipelineContext<*, ApplicationCall>.requirePermission(permission: OrganizationPermission) {
    val orgId = call.requireParameter("organizationId").toLong()
    requirePermission(permission.roleName(orgId))
}

/**
 * Require that the [OrtPrincipal] of the current[call] has the provided [permission]. Throw an [AuthorizationException]
 * otherwise.
 */
fun PipelineContext<*, ApplicationCall>.requirePermission(permission: ProductPermission) {
    val productId = call.requireParameter("productId").toLong()
    requirePermission(permission.roleName(productId))
}

/**
 * Require that the [OrtPrincipal] of the current[call] has the provided [permission]. Throw an [AuthorizationException]
 * otherwise.
 */
fun PipelineContext<*, ApplicationCall>.requirePermission(permission: RepositoryPermission) {
    val repositoryId = call.requireParameter("repositoryId").toLong()
    requirePermission(permission.roleName(repositoryId))
}

/**
 * Require that the [OrtPrincipal] of the current [call] has the [requiredRole]. Throw an [AuthorizationException]
 * otherwise.
 */
fun PipelineContext<*, ApplicationCall>.requirePermission(requiredRole: String) {
    val principal = call.principal<OrtPrincipal>()
    if (!principal.isSuperuser() && !principal.hasRole(requiredRole)) {
        throw AuthorizationException()
    }
}

/**
 * Require that the [OrtPrincipal] of the current [call] is a [Superuser]. Throw an [AuthorizationException] otherwise.
 */
fun PipelineContext<*, ApplicationCall>.requireSuperuser() {
    requirePermission(Superuser.ROLE_NAME)
}
