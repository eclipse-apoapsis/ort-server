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

package org.eclipse.apoapsis.ortserver.model

import kotlinx.serialization.Serializable

/**
 * User information to be displayed in the UI.
 */
@Serializable
data class UserDisplayName(
    /**
     * Identifier assigned to each user in Keycloak, used to distinguish users across sessions and services.
     * This identifier is stable over time and is unique to each user.
     */
    val userId: String,

    /**
     * Unique, human-readable identifier chosen by the user or administrator, used for authentication and login.
     * Unlike the immutable [userId], this identifier can change over time.
     */
    val username: String,

    /**
     * Full name of the user: A derived attribute that typically combines a user's first name and last name,
     * providing a readable display name but not serving as a unique identifier. This can change over time.
     */
    val fullName: String? = null
)
