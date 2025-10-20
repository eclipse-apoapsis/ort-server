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

import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId

/**
 * An interface allowing access to all permissions a specific user has for a concrete element in the hierarchy.
 *
 * An instance of this interface is typically created for each request affecting a hierarchy element. Via the methods
 * provided here, client code can check whether the user has the required permissions to perform the requested action.
 */
interface EffectiveRole {
    /**
     * The compound ID of the hierarchy element this effective role applies to. This object contains the aggregated
     * permissions of the current user for this element.
     */
    val elementId: CompoundHierarchyId

    /**
     * A flag indicating whether the associated user has superuser rights.
     */
    val isSuperuser: Boolean

    /**
     * Check whether this effective role grants the given [permission] on the organization level.
     */
    fun hasOrganizationPermission(permission: OrganizationPermission): Boolean

    /**
     * Check whether this effective role grants the given [permission] on the product level.
     */
    fun hasProductPermission(permission: ProductPermission): Boolean

    /**
     * Check whether this effective role grants the given [permission] on the repository level.
     */
    fun hasRepositoryPermission(permission: RepositoryPermission): Boolean
}
