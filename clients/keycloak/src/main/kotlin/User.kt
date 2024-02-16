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

import kotlinx.serialization.Serializable

/**
 * A data class representing a user managed by Keycloak.
 */
@Serializable
data class User(
    /** The internal ID of the user. */
    val id: UserId,

    /** The username of the user. */
    val username: UserName,

    /** The first name of the user. */
    val firstName: String? = null,

    /** The last name of the user. */
    val lastName: String? = null,

    /** The mail address of the user. */
    val email: String? = null,

    /** Specifies, whether the user can log in or not. */
    val enabled: Boolean = true
)
