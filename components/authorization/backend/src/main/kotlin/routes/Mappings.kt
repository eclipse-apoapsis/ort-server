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

import org.eclipse.apoapsis.ortserver.components.authorization.api.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.api.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.api.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.api.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.api.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.api.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationPermission as ModelOrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole as ModelOrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductPermission as ModelProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole as ModelProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission as ModelRepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole as ModelRepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.Role
import org.eclipse.apoapsis.ortserver.model.UserGroup

fun OrganizationRole.mapToModel() = when (this) {
    OrganizationRole.READER -> ModelOrganizationRole.READER
    OrganizationRole.WRITER -> ModelOrganizationRole.WRITER
    OrganizationRole.ADMIN -> ModelOrganizationRole.ADMIN
}

fun ProductRole.mapToModel() = when (this) {
    ProductRole.READER -> ModelProductRole.READER
    ProductRole.WRITER -> ModelProductRole.WRITER
    ProductRole.ADMIN -> ModelProductRole.ADMIN
}

fun RepositoryRole.mapToModel() = when (this) {
    RepositoryRole.READER -> ModelRepositoryRole.READER
    RepositoryRole.WRITER -> ModelRepositoryRole.WRITER
    RepositoryRole.ADMIN -> ModelRepositoryRole.ADMIN
}

fun ModelOrganizationPermission.mapToApi() = when (this) {
    ModelOrganizationPermission.READ -> OrganizationPermission.READ
    ModelOrganizationPermission.WRITE -> OrganizationPermission.WRITE
    ModelOrganizationPermission.WRITE_SECRETS -> OrganizationPermission.WRITE_SECRETS
    ModelOrganizationPermission.MANAGE_GROUPS -> OrganizationPermission.MANAGE_GROUPS
    ModelOrganizationPermission.READ_PRODUCTS -> OrganizationPermission.READ_PRODUCTS
    ModelOrganizationPermission.CREATE_PRODUCT -> OrganizationPermission.CREATE_PRODUCT
    ModelOrganizationPermission.DELETE -> OrganizationPermission.DELETE
}

fun ModelProductPermission.mapToApi() = when (this) {
    ModelProductPermission.READ -> ProductPermission.READ
    ModelProductPermission.WRITE -> ProductPermission.WRITE
    ModelProductPermission.WRITE_SECRETS -> ProductPermission.WRITE_SECRETS
    ModelProductPermission.MANAGE_GROUPS -> ProductPermission.MANAGE_GROUPS
    ModelProductPermission.READ_REPOSITORIES -> ProductPermission.READ_REPOSITORIES
    ModelProductPermission.CREATE_REPOSITORY -> ProductPermission.CREATE_REPOSITORY
    ModelProductPermission.TRIGGER_ORT_RUN -> ProductPermission.TRIGGER_ORT_RUN
    ModelProductPermission.DELETE -> ProductPermission.DELETE
}

fun ModelRepositoryPermission.mapToApi() = when (this) {
    ModelRepositoryPermission.READ -> RepositoryPermission.READ
    ModelRepositoryPermission.WRITE -> RepositoryPermission.WRITE
    ModelRepositoryPermission.WRITE_SECRETS -> RepositoryPermission.WRITE_SECRETS
    ModelRepositoryPermission.MANAGE_GROUPS -> RepositoryPermission.MANAGE_GROUPS
    ModelRepositoryPermission.MANAGE_RESOLUTIONS -> RepositoryPermission.MANAGE_RESOLUTIONS
    ModelRepositoryPermission.READ_ORT_RUNS -> RepositoryPermission.READ_ORT_RUNS
    ModelRepositoryPermission.TRIGGER_ORT_RUN -> RepositoryPermission.TRIGGER_ORT_RUN
    ModelRepositoryPermission.DELETE -> RepositoryPermission.DELETE
}

/**
 * Map this [Role] to a [UserGroup] which is required by the endpoint for querying user / role information.
 */
fun Role.mapToGroup(): UserGroup =
    when (name) {
        "READER" -> UserGroup.READERS
        "WRITER" -> UserGroup.WRITERS
        "ADMIN" -> UserGroup.ADMINS
        else -> throw IllegalArgumentException("Cannot map role '$name' to a user group.")
    }
