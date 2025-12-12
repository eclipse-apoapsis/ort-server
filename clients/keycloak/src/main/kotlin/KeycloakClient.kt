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

package org.eclipse.apoapsis.ortserver.clients.keycloak

/**
 * A client implementing interactions with Keycloak, based on the documentation from
 * https://www.keycloak.org/docs-api/19.0/rest-api/index.html.
 */
@Suppress("TooManyFunctions")
interface KeycloakClient {
    /**
     * Return a set of all [users][User], which currently exist in the Keycloak realm.
     */
    suspend fun getUsers(): Set<User>

    /**
     * Return the [user][User] with the given [username].
     */
    suspend fun getUser(username: UserName): User

    /**
     * Create a new [user][User] in the Keycloak realm with the given [username], [firstName], [lastName], [email],
     * [password] and [temporary].
     */
    suspend fun createUser(
        username: UserName,
        firstName: String? = null,
        lastName: String? = null,
        email: String? = null,
        password: String? = null,
        temporary: Boolean = false
    )

    /**
     * Update the [user][User] with the given [id] within the Keycloak realm with the new [username], [firstName],
     * [lastName] and [email].
     */
    suspend fun updateUser(
        id: UserId,
        username: UserName? = null,
        firstName: String? = null,
        lastName: String? = null,
        email: String? = null
    )

    /**
     * Delete the [user][User] with the given [id] from the Keycloak realm.
     */
    suspend fun deleteUser(id: UserId)

    /**
     * Return a set of all [users][User] of a group [GroupName].
     */
    suspend fun getGroupMembers(groupName: GroupName): Set<User>

    /**
     * Return a set of all [users][User] of a group [GroupId].
     */
    suspend fun getGroupMembers(groupId: GroupId): Set<User>
}
