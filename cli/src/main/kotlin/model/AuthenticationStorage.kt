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

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

import org.eclipse.apoapsis.ortserver.cli.utils.configDir

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
        if (authFile.isFile) {
            val savedAuth = Yaml.default.decodeFromString<List<HostAuthenticationDetails>>(authFile.readText())

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
        authFile.writeText(Yaml.default.encodeToString(listOf(storage)))

        // Ensure file permissions 600, as this file contains security relevant information.
        Files.setPosixFilePermissions(
            authFile.toPath(),
            PosixFilePermissions.asFileAttribute(
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
                )
            ).value()
        )
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
