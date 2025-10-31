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

package org.eclipse.apoapsis.ortserver.components.authorization.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.User as KeycloakUser
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName
import org.eclipse.apoapsis.ortserver.model.User

/**
 * An implementation of the [UserService] interface that uses Keycloak as the backend user management system. As unique
 * IDs for users, it user the _username_ attribute in Keycloak.
 */
class KeycloakUserService(
    /** The client for interacting with Keycloak. */
    private val keycloakClient: KeycloakClient
) : UserService {
    override suspend fun createUser(
        username: String,
        firstName: String?,
        lastName: String?,
        email: String?,
        password: String?,
        temporary: Boolean
    ) {
        keycloakClient.createUser(
            username = UserName(username),
            firstName = firstName,
            lastName = lastName,
            email = email,
            password = password,
            temporary = temporary
        )
    }

    override suspend fun deleteUser(username: String) {
        val userId = keycloakClient.getUser(UserName(username)).id
        keycloakClient.deleteUser(userId)
    }

    override suspend fun getUsers(): Set<User> =
        keycloakClient.getUsers().mapTo(mutableSetOf()) { it.toOrtUser() }

    override suspend fun getUserById(id: String): User =
        keycloakClient.getUser(UserName(id)).toOrtUser()

    override suspend fun getUsersById(ids: Set<String>): Set<User> = withContext(Dispatchers.IO) {
        ids.map { async { getUserById(it) } }
            .mapTo(mutableSetOf()) { it.await() }
    }

    override suspend fun userExists(id: String): Boolean =
        runCatching { getUserById(id) }.isSuccess
}

/**
 * Convert this [KeycloakUser] to a [User] in the ORT Server data model.
 */
private fun KeycloakUser.toOrtUser(): User =
    User(
        username = this.username.value,
        firstName = this.firstName,
        lastName = this.lastName,
        email = this.email
    )
