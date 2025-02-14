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

package org.eclipse.apoapsis.ortserver.cli.model

import com.charleskorn.kaml.Yaml

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

import org.eclipse.apoapsis.ortserver.cli.utils.configDir
import org.eclipse.apoapsis.ortserver.cli.utils.delete
import org.eclipse.apoapsis.ortserver.cli.utils.exists
import org.eclipse.apoapsis.ortserver.cli.utils.mkdirs
import org.eclipse.apoapsis.ortserver.cli.utils.setPermissionsToOwnerReadWrite
import org.eclipse.apoapsis.ortserver.cli.utils.toSource
import org.eclipse.apoapsis.ortserver.cli.utils.write

private const val AUTH_FILE_NAME = "auth.yml"

/**
 * Object to help with storing and retrieving authentication details.
 *
 * Note: Currently only single host authentication is supported.
 */
internal object AuthenticationStorage {
    private val authFile = configDir.resolve(AUTH_FILE_NAME)

    private var storage: HostAuthenticationDetails? = null

    init {
        if (authFile.exists()) {
            val savedAuth = Yaml.default.decodeFromSource<List<HostAuthenticationDetails>>(authFile.toSource())

            storage = savedAuth.firstOrNull()
        }
    }

    /**
     * Store the [authentication] on the user's disk.
     */
    fun store(authentication: HostAuthenticationDetails) {
        storage = authentication

        saveToFile()
    }

    /**
     * Read the stored authentication details.
     */
    fun get(): HostAuthenticationDetails? = storage

    /**
     * Delete any existing authentication.
     */
    fun clear() {
        storage = null

        authFile.delete()
    }

    private fun saveToFile() {
        if (authFile.parent?.exists() == false) authFile.parent?.mkdirs()

        authFile.write(Yaml.default.encodeToString(listOf(storage)))

        authFile.setPermissionsToOwnerReadWrite()
    }
}

/**
 * Data class to store the authentication details for a single ORT Server host.
 */
@Serializable
internal data class HostAuthenticationDetails(
    /** The base URL of the ORT server instance. */
    val baseUrl: String,

    /** The URL to request new access and refresh tokens. */
    val tokenUrl: String,

    /** The client ID to use for authentication. */
    val clientId: String,

    /** The username used for the authentication. */
    val username: String,

    /** The tokens required for the authentication. */
    val tokens: Tokens
)

@Serializable
internal data class Tokens(
    val access: String,
    val refresh: String
)
