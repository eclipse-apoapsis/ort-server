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

import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName
import org.eclipse.apoapsis.ortserver.model.User

/**
 * A service providing functions for working with [users][User].
 */
class UserService(
    private val keycloakClient: KeycloakClient
) {
    /**
     * Create a user. If "password" is null, then "temporary" is ignored.
     */
    suspend fun createUser(username: String, password: String?, temporary: Boolean) = run {
        keycloakClient.createUser(username = UserName(username), password = password, temporary = temporary)
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
}
