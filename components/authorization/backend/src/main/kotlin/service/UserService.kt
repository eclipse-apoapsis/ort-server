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

import org.eclipse.apoapsis.ortserver.model.User

/**
 * A service interface to manage users for this server instance. The service
 */
interface UserService {
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
    )

    /**
     * Delete the user with the given [username] from the server.
     */
    suspend fun deleteUser(username: String)

    /**
     * Get all current users of the server.
     */
    suspend fun getUsers(): Set<User>

    /**
     * Return the user with the given internal [id]. Throw an exception if the user does not exist.
     */
    suspend fun getUserById(id: String): User

    /**
     * Resolve a number of users by their internal [ids]. Ignore unknown ids.
     */
    suspend fun getUsersById(ids: Set<String>): Set<User>

    /**
     * Return a flag whether a user with the given [id] exists.
     */
    suspend fun existsUser(id: String): Boolean
}
