/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.api.v1.model

import kotlinx.serialization.Serializable

/**
 * Response object for a user.
 */
@Serializable
data class User(
    /** The username of the user. */
    val username: String,

    /** The first name of the user. */
    val firstName: String? = null,

    /** The last name of the user. */
    val lastName: String? = null,

    /** The mail address of the user. */
    val email: String? = null,
)

/**
 * User group (privilege level) for repositories, products or organizations.
 */
@Serializable
enum class UserGroup(private val rank: Int) {
    /** READER privilege */
    READERS(1),

    /** WRITER privilege */
    WRITERS(2),

    /** ADMIN privilege */
    ADMINS(3);

    fun getRank() = rank
}

/**
 * Response object for a user containing groups that user belongs to.
 */
@Serializable
data class UserWithGroups(
    /** User object */
    val user: User,

    /** List of groups user belongs to */
    val groups: List<UserGroup>
)

@Serializable
data class CreateUser(
    val username: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val password: String? = null,

    /** Specifies whether the password is for one-time use only */
    val temporary: Boolean = true
)

/**
 * Request object for identifying a user by name.
 */
@Serializable
data class Username(
    val username: String
)
