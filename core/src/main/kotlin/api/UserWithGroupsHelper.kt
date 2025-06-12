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
import org.eclipse.apoapsis.ortserver.api.v1.model.UserWithGroups
import org.eclipse.apoapsis.ortserver.core.utils.paginate
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.model.User
import org.eclipse.apoapsis.ortserver.model.UserGroup
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingOptions
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection

/**
 * Sort and paginate the list of [UserWithGroups] by the given [PagingOptions].
 * As records returned from Keycloak have no sort and paging capabilities, this class is used to do so.
 * Although the API supports having more than one sort order field, this implementation only supports a single
 * sort field.
 */
internal object UserWithGroupsHelper {
    internal fun List<UserWithGroups>.sortAndPage(paging: PagingOptions): List<UserWithGroups> {
        if (paging.sortProperties?.size != 1) {
            throw QueryParametersException("Exactly one sort field must be defined.")
        }

        val sortField = paging.sortProperties?.first()
        requireNotNull(sortField) {
            "Exactly one sort field must be defined."
        }

        return when (sortField.name) {
            "username" -> compareBy<UserWithGroups> { it.user.username }
            "firstName" -> compareBy<UserWithGroups> { it.user.firstName }
            "lastName" -> compareBy<UserWithGroups> { it.user.lastName }
            "email" -> compareBy<UserWithGroups> { it.user.email }
            "group" -> compareBy<UserWithGroups> { it.groups.minBy { group -> group.getRank() } }
            "" -> throw QueryParametersException("Empty sort field.")
            else -> throw QueryParametersException("Unknown sort field '${sortField.name}'.")
        }.let {
            sortedWith(it)
        }.let {
            if (sortField.direction == SortDirection.DESCENDING) it.reversed() else it
        }.paginate(paging)
    }

    internal fun Map<User, Set<UserGroup>>.mapToApi(): List<UserWithGroups> = map { user ->
        UserWithGroups(
            user.key.mapToApi(),
            user.value.map { it.mapToApi() }.toList().sortedBy { it.getRank() }.reversed()
        )
    }
}
