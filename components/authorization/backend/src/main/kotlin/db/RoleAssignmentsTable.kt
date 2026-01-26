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

package org.eclipse.apoapsis.ortserver.components.authorization.db

import org.eclipse.apoapsis.ortserver.dao.repositories.organization.OrganizationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * A database table for storing role assignments to users for hierarchy elements.
 *
 * Roles can be assigned to users on all levels of the hierarchy. An entry in the table stores not only the ID of
 * the element the assignment is about, but also the IDs of all parent elements. This simplifies use cases to
 * inherit permissions down the hierarchy and to find the elements a user has access to.
 *
 * A single entity references exactly one role. There are, however, multiple columns for role references to distinguish
 * between the levels in the hierarchy. This is needed to identify the correct role class which then grants permissions
 * on the different levels. (Since any role can provide permissions on multiple levels, there is no strict 1:1
 * mapping between hierarchy elements and roles. When evaluating permissions, the union of all permissions from all
 * roles on all levels is constructed.)
 */
object RoleAssignmentsTable : LongIdTable("role_assignments") {
    /** The ID of the user who is subject of this assignment. */
    val userId = text("user_id")

    /** The optional organization ID this assignment refers to. */
    val organizationId = reference("organization_id", OrganizationsTable).nullable()

    /** The optional product ID this assignment refers to. */
    val productId = reference("product_id", ProductsTable).nullable()

    /** The optional repository ID this assignment refers to. */
    val repositoryId = reference("repository_id", RepositoriesTable).nullable()

    /** The name of the role on organization level if defined. */
    val organizationRole = text("organization_role").nullable()

    /** The name of the role on product level if defined. */
    val productRole = text("product_role").nullable()

    /** The name of the role on repository level if defined. */
    val repositoryRole = text("repository_role").nullable()
}
