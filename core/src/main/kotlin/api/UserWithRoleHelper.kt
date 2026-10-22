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

package org.eclipse.apoapsis.ortserver.core.api

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.model.User as ApiUser
import org.eclipse.apoapsis.ortserver.components.authorization.rights.Role
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RoleInfo
import org.eclipse.apoapsis.ortserver.components.authorization.service.UserService
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.model.User
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingOptions
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.ktorutils.paginate

/**
 * Sort and paginate lists of users with their roles by the given [PagingOptions].
 * As the users are not read from the database in a single query, the sorting and paging is done in memory.
 * Although the API supports having more than one sort order field, this implementation only supports a single
 * sort field.
 */
internal object UserWithRoleHelper {
    /**
     * Sort and paginate this list by the given [paging] options. The [user] and [role] functions return the user and
     * the role of an item. Roles are sorted by their order of declaration, which puts readers before writers before
     * admins.
     */
    internal fun <T> List<T>.sortAndPage(
        paging: PagingOptions,
        user: (T) -> ApiUser,
        role: (T) -> Enum<*>
    ): List<T> {
        if (paging.sortProperties?.size != 1) {
            throw QueryParametersException("Exactly one sort field must be defined.")
        }

        val sortField = paging.sortProperties?.first()
        requireNotNull(sortField) {
            "Exactly one sort field must be defined."
        }

        return when (sortField.name) {
            "username" -> compareBy<T> { user(it).username }
            "firstName" -> compareBy<T> { user(it).firstName }
            "lastName" -> compareBy<T> { user(it).lastName }
            "email" -> compareBy<T> { user(it).email }
            "role" -> compareBy<T> { role(it).ordinal }
            "" -> throw QueryParametersException("Empty sort field.")
            else -> throw QueryParametersException("Unknown sort field '${sortField.name}'.")
        }.let {
            sortedWith(it)
        }.let {
            if (sortField.direction == SortDirection.DESCENDING) it.reversed() else it
        }.paginate(paging)
    }

    /**
     * Convert this [Map] with information about users and their assigned roles to a list of items that can be used
     * to display user / role information for a specific hierarchy element. The [toApi] function builds an item from
     * a user and its role. Apply the given [roleFilter] to select only specific items based on their role
     * information. This can be used for instance, to distinguish between users with an explicit role assignment and
     * users who only have inherited roles.
     */
    internal suspend fun <T> Map<String, RoleInfo>.mapToApi(
        userService: UserService,
        roleFilter: (RoleInfo) -> Boolean = { true },
        toApi: (ApiUser, Role) -> T
    ): List<T> {
        val userMapping = userService.getUsersById(keys).associateBy(User::username)

        return filter { it.key in userMapping && roleFilter(it.value) }
            .map { (username, roleInfo) -> toApi(userMapping.getValue(username).mapToApi(), roleInfo.role) }
    }
}
