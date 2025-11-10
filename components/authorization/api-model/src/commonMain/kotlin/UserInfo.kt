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

package org.eclipse.apoapsis.ortserver.components.authorization.api

import kotlinx.serialization.Serializable

/**
 * A data class that provides information about the current user. An instance of this class is returned by the
 * _/authorization/userinfo_ endpoint. It is used by the UI to determine the set of the user's permissions, so that the
 * UI can adapt itself accordingly, for instance, by hiding or disabling actions the user is not allowed to perform.
 */
@Serializable
data class UserInfo(
    /** The username of the current user. */
    val username: String,

    /** The full name of the current user. */
    val fullName: String,

    /**
     * A set with the names of permissions the current user has on the current level of the hierarchy.
     */
    val permissions: Set<String>,

    /** A flag whether the current user is a superuser. */
    val isSuperuser: Boolean
)
