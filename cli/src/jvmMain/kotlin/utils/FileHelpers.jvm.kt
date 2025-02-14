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

package org.eclipse.apoapsis.ortserver.cli.utils

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission

import okio.Path
import okio.Path.Companion.toPath

import org.eclipse.apoapsis.ortserver.cli.getEnv

@Suppress("SwallowedException")
internal actual fun Path.setPermissionsToOwnerReadWrite() {
    val file = Paths.get(toString())
    val permissions = setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE
    )

    try {
        Files.setPosixFilePermissions(file, permissions)
    } catch (e: UnsupportedOperationException) {
        // Fallback for non-POSIX systems (e.g., Windows).
        val javaFile = file.toFile()

        javaFile.setReadable(true, true)
        javaFile.setWritable(true, true)
        javaFile.setExecutable(false)
    }
}

/**
 * Get the user's home directory as a [Path].
 */
actual fun getHomeDirectory() = fixupUserHomeProperty().toPath()

/**
 * Check if the "user.home" property is set to a sane value and otherwise set it to the value of an (OS-specific)
 * environment variable for the user home directory, and return that value. This works around the issue that esp. in
 * certain Docker scenarios "user.home" is set to "?", see https://bugs.openjdk.java.net/browse/JDK-8193433 for some
 * background information.
 *
 * This code is taken from
 * https://github.com/oss-review-toolkit/ort/blob/a25942a748a31e324dd3032acfacf5f05cc40a9f/utils/common/src/main/kotlin/Os.kt#L69-L89
 */
fun fixupUserHomeProperty(): String {
    val userHome = System.getProperty("user.home")
    if (!userHome.isNullOrBlank() && userHome != "?") return userHome

    val fallbackUserHome = listOfNotNull(
        getEnv("HOME"),
        getEnv("USERPROFILE")
    ).find {
        it.isNotBlank()
    } ?: throw IllegalArgumentException("Unable to determine a user home directory.")

    System.setProperty("user.home", fallbackUserHome)

    return fallbackUserHome
}
