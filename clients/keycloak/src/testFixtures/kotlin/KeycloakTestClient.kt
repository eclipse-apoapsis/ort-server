/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.clients.keycloak.test

import org.eclipse.apoapsis.ortserver.clients.keycloak.Group
import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupId
import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupName
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClientException
import org.eclipse.apoapsis.ortserver.clients.keycloak.Role
import org.eclipse.apoapsis.ortserver.clients.keycloak.RoleId
import org.eclipse.apoapsis.ortserver.clients.keycloak.RoleName
import org.eclipse.apoapsis.ortserver.clients.keycloak.User
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserCredentials
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserId
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName

/**
 * An implementation of [KeycloakClient] that can be used for testing, for example, when using the Keycloak
 * testcontainer is too expensive and mocking the client becomes too complex.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class KeycloakTestClient(
    private val groups: MutableSet<Group> = mutableSetOf(),
    private val groupClientRoles: MutableMap<GroupId, Set<RoleId>> = mutableMapOf(),
    private val roles: MutableSet<Role> = mutableSetOf(),
    private val roleComposites: MutableMap<RoleId, Set<RoleId>> = mutableMapOf(),
    private val users: MutableSet<User> = mutableSetOf(),
    private val userClientRoles: MutableMap<UserId, Set<RoleId>> = mutableMapOf(),
    private val userGroups: MutableMap<UserId, Set<GroupId>> = mutableMapOf()

) : KeycloakClient {
    private var groupCounter = 0
    private var roleCounter = 0
    private var userCounter = 0
    private val credentials = mutableMapOf<UserId, MutableList<UserCredentials>>()

    override suspend fun getGroups(groupNameFilter: String?) =
        groupNameFilter?.let {
            groups.filter { group -> group.name.value.contains(groupNameFilter) }.toSet()
        } ?: groups

    override suspend fun getGroup(id: GroupId) =
        groups.find { it.id == id } ?: throw KeycloakClientException("")

    override suspend fun getGroup(name: GroupName) =
        groups.find { it.name == name } ?: throw KeycloakClientException("")

    override suspend fun searchGroups(name: GroupName) =
        groups.filter { it.name.value.contains(name.value) }.toSet()

    override suspend fun createGroup(name: GroupName) {
        if (groups.any { it.name == name }) throw KeycloakClientException("")
        val id = getNextGroupId()
        groups += Group(id, name)
        groupClientRoles[id] = emptySet()
    }

    override suspend fun updateGroup(id: GroupId, name: GroupName) {
        if (groups.any { it.name == name }) throw KeycloakClientException("")
        val group = getGroup(id)
        groups -= group
        groups += group.copy(name = name)
    }

    override suspend fun deleteGroup(id: GroupId) {
        val group = getGroup(id)
        groups -= group
        groupClientRoles -= id
    }

    override suspend fun getGroupClientRoles(id: GroupId) =
        groupClientRoles[id]?.flatMapTo(mutableSetOf()) { getCompositeRolesRecursive(it) + getRole(it) }
            ?: throw KeycloakClientException("")

    override suspend fun addGroupClientRole(id: GroupId, role: Role) {
        val roles = groupClientRoles[id] ?: throw KeycloakClientException("")
        groupClientRoles[id] = roles + getRole(role.id).id
    }

    override suspend fun removeGroupClientRole(id: GroupId, role: Role) {
        val roles = groupClientRoles[id] ?: throw KeycloakClientException("")
        groupClientRoles[id] = roles - getRole(role.id).id
    }

    override suspend fun getRoles(): Set<Role> = roles

    override suspend fun getRole(name: RoleName) = roles.find { it.name == name } ?: throw KeycloakClientException("")

    override suspend fun createRole(name: RoleName, description: String?) {
        if (roles.any { it.name == name }) throw KeycloakClientException("")
        val id = getNextRoleId()
        roles += Role(id, name, description)
        roleComposites[id] = emptySet()
    }

    override suspend fun updateRole(name: RoleName, updatedName: RoleName, updatedDescription: String?) {
        if (name != updatedName && roles.any { it.name == updatedName }) throw KeycloakClientException("")
        val role = getRole(name)
        roles -= role
        roles += role.copy(name = updatedName, description = updatedDescription)
    }

    override suspend fun deleteRole(name: RoleName) {
        val role = getRole(name)
        roles -= role
        roleComposites -= role.id
    }

    override suspend fun addCompositeRole(name: RoleName, compositeRoleId: RoleId) {
        val role = getRole(name)
        val compositeRole = getRole(compositeRoleId)
        val compositeRoles = roleComposites[role.id] ?: throw KeycloakClientException("")
        roleComposites[role.id] = compositeRoles + compositeRole.id
    }

    override suspend fun getCompositeRoles(name: RoleName): List<Role> {
        val role = getRole(name)
        return getCompositeRolesRecursive(role.id).toList()
    }

    override suspend fun removeCompositeRole(name: RoleName, compositeRoleId: RoleId) {
        val role = getRole(name)
        val compositeRole = getRole(compositeRoleId)
        val compositeRoles = roleComposites[role.id] ?: throw KeycloakClientException("")
        roleComposites[role.id] = compositeRoles - compositeRole.id
    }

    override suspend fun getUsers() = users

    override suspend fun getUser(id: UserId) = users.find { it.id == id } ?: throw KeycloakClientException("")

    override suspend fun getUser(username: UserName) =
        users.find { it.username == username } ?: throw KeycloakClientException("")

    override suspend fun createUser(
        username: UserName,
        firstName: String?,
        lastName: String?,
        email: String?,
        password: String?,
        temporary: Boolean
    ) {
        if (users.any { it.username == username }) throw KeycloakClientException("")
        val id = getNextUserId()
        users += User(id, username, firstName, lastName, email)
        userClientRoles[id] = emptySet()
        if (password != null) credentials.getOrPut(id) { mutableListOf() } += UserCredentials("", "", 0, "")
    }

    override suspend fun updateUser(
        id: UserId,
        username: UserName?,
        firstName: String?,
        lastName: String?,
        email: String?
    ) {
        val user = getUser(id)
        if (user.username != username && users.any { it.username == username }) throw KeycloakClientException("")
        if (email != null && user.email != email && users.any { it.email == email }) throw KeycloakClientException("")

        users -= user
        users += user.copy(
            username = username ?: user.username,
            firstName = firstName ?: user.firstName,
            lastName = lastName ?: user.lastName,
            email = email ?: user.email
        )
    }

    override suspend fun deleteUser(id: UserId) {
        val user = getUser(id)
        users -= user
        userClientRoles -= id
    }

    override suspend fun getUserClientRoles(id: UserId) =
        userClientRoles[id]?.flatMapTo(mutableSetOf()) { getCompositeRolesRecursive(it) + getRole(it) }
            ?: throw KeycloakClientException("")

    override suspend fun addUserToGroup(username: UserName, groupName: GroupName) {
        val user = getUser(username)
        val group = getGroup(groupName)
        userGroups[user.id]?.let {
            userGroups[user.id] = it + group.id
        } ?: run {
            userGroups[user.id] = setOf(group.id)
        }
    }

    override suspend fun removeUserFromGroup(username: UserName, groupName: GroupName) {
        TODO("Not yet implemented")
    }

    override suspend fun getGroupMembers(groupName: GroupName): Set<User> {
        val groupId = groups.find { it.name == groupName }?.id ?: throw KeycloakClientException("")
        return getGroupMembers(groupId)
    }

    override suspend fun getGroupMembers(groupId: GroupId): Set<User> {
        val userIds = userGroups.filter { it.value.contains(groupId) }.keys
        return users.filter { it.id in userIds }.toSet()
    }

    override suspend fun getUserHasCredentials(username: UserName): Boolean {
        val user = getUser(username)
        val creds = credentials[user.id]
        return !creds.isNullOrEmpty()
    }

    private fun getNextGroupId() = GroupId("group-id-${groupCounter++}")

    private fun getNextRoleId() = RoleId("role-id-${roleCounter++}")

    private fun getNextUserId() = UserId("user-id-${userCounter++}")

    private fun getRole(id: RoleId) = roles.find { it.id == id } ?: throw KeycloakClientException("")

    private fun getCompositeRolesRecursive(id: RoleId, visited: Set<RoleId> = emptySet()): Set<Role> {
        if (id in visited) return emptySet()
        val compositeRoles = roleComposites[id] ?: throw KeycloakClientException("")
        return buildSet {
            addAll(compositeRoles.map { getRole(it) })
            compositeRoles.forEach {
                addAll(getCompositeRolesRecursive(it, visited + it))
            }
        }
    }
}
