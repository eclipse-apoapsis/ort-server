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

package org.eclipse.apoapsis.ortserver.components.authorization.rights

/**
 * An interface to define common properties of all roles in the authorization system.
 *
 * A role mainly bundles a set of permissions; typically on the same hierarchical level as the role is defined for.
 * In some situations, however, a role also impacts other levels. For example, a user who has the _READER_ role for a
 * specific repository should also be entitled to view the product and the organization the repository belongs to.
 */
sealed interface Role {
    /** A set with permissions that are granted by this role on the organization level. */
    val organizationPermissions: Set<OrganizationPermission>

    /** A set with permissions that are granted by this role on the product level. */
    val productPermissions: Set<ProductPermission>

    /** A set with permissions that are granted by this role on the repository level. */
    val repositoryPermissions: Set<RepositoryPermission>
}
