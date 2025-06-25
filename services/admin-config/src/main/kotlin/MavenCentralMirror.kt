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

package org.eclipse.apoapsis.ortserver.services.config

/**
 * A data class representing a Maven Central mirror configuration.
 */
data class MavenCentralMirror(
    /** The unique identifier of the Maven Central mirror. */
    val id: String,

    /** The name of the Maven Central mirror. */
    val name: String,

    /** The URL of the Maven Central mirror. */
    val url: String,

    /** The identifier of the repositories that this mirror should apply to. */
    val mirrorOf: String,

    /** The name of the secret that contains the username as value. */
    val usernameSecret: String? = null,

    /** The name of the secrets that contains the password as value. */
    val passwordSecret: String? = null
)
