/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.services

import org.eclipse.apoapsis.ortserver.clients.keycloak.Group
import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupName
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName
import org.eclipse.apoapsis.ortserver.components.authorization.roles.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.RepositoryRole
import org.eclipse.apoapsis.ortserver.model.User
import org.eclipse.apoapsis.ortserver.model.UserGroup

/**
 * A service providing functions for working with [users][User].
 */
class UserService(
    private val keycloakClient: KeycloakClient
) {
    /**
     * Create a user. If "password" is null, then "temporary" is ignored.
     */
    @Suppress("LongParameterList")
    suspend fun createUser(
        username: String,
        firstName: String?,
        lastName: String?,
        email: String?,
        password: String?,
        temporary: Boolean
    ) = run {
        keycloakClient.createUser(
            username = UserName(username),
            firstName = firstName,
            lastName = lastName,
            email = email,
            password = password,
            temporary = temporary
        )
    }

    /**
     * Get all current users of the server.
     */
    suspend fun getUsers(): Set<User> = run {
        keycloakClient.getUsers().map {
            User(
                username = it.username.value,
                firstName = it.firstName,
                lastName = it.lastName,
                email = it.email
            )
        }.toSet()
    }

    /**
     * Delete a user from the server.
     */
    suspend fun deleteUser(username: String) = run {
        // Get the user ID by username
        val user = keycloakClient.getUser(username = UserName(username))
        keycloakClient.deleteUser(id = user.id)
    }

    /**
     * Get [User]s with all the [UserGroup]s assigned to them (ADMINS, WRITERS, READERS), that have access to the
     * organization with this [organizationId].
     */
    suspend fun getUsersHavingRightsForOrganization(organizationId: Long): Map<User, Set<UserGroup>> =
        getUsersForGroups(keycloakClient.searchGroups(GroupName(OrganizationRole.groupPrefix(organizationId))))

    /**
     * Get [User]s with all the [UserGroup]s assigned to them (ADMINS, WRITERS, READERS), that have access to the
     * product with this [productId].
     */
    suspend fun getUsersHavingRightForProduct(productId: Long): Map<User, Set<UserGroup>> =
        getUsersForGroups(keycloakClient.searchGroups(GroupName(ProductRole.groupPrefix(productId))))

    /**
     * Get [User]s with all  the [UserGroup]s assigned to them (ADMINS, WRITERS, READERS), that have rights to the
     * repository with this [repositoryId].
     */
    suspend fun getUsersHavingRightsForRepository(repositoryId: Long): Map<User, Set<UserGroup>> =
        getUsersForGroups(keycloakClient.searchGroups(GroupName(RepositoryRole.groupPrefix(repositoryId))))

    private suspend fun getUsersForGroups(groups: Set<Group>): Map<User, Set<UserGroup>> {
        val users = mutableMapOf<User, Set<UserGroup>>()
        groups.forEach { group ->
            keycloakClient.getGroupMembers(group.id).map {
                val user = User(
                    username = it.username.value,
                    firstName = it.firstName,
                    lastName = it.lastName,
                    email = it.email
                )

                users[user]?.let { groupSet ->
                    users[user] = groupSet + calculateUserGroupName(group)
                } ?: run {
                    users[user] = mutableSetOf(calculateUserGroupName(group))
                }
            }
        }
        return users
    }

    /**
     * Keycloak group name format is: "<ENTITY_TYPE>_<ENTITY_ID>_<ROLE>".
     * By extracting the "ROLE" part from group name, we can map it to [UserGroup].
     * In example, group name "ORGANIZATION_99_ADMINS" will be mapped to [UserGroup.ADMINS].
     * @see: [ProductRole.groupName], [OrganizationRole.groupName], [RepositoryRole.groupName].
     */
    private fun calculateUserGroupName(group: Group): UserGroup =
        group.name.value.split("_").last().let {
            UserGroup.valueOf(it)
        }
}
