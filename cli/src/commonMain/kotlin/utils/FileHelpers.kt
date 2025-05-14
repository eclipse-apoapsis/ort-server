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

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.Source
import okio.buffer
import okio.use

/**
 * Get the current working directory as a [Path].
 */
internal val currentWorkingDir: Path
    get() = FileSystem.SYSTEM.canonicalize(".".toPath())

/**
 * Create a [Source] for this [Path].
 */
internal fun Path.toSource() = FileSystem.SYSTEM.source(this)

/**
 * Delete the file or directory at this [Path].
 */
internal fun Path.delete() = FileSystem.SYSTEM.delete(this)

/**
 * Check if the file or directory at this [Path] exists.
 */
internal fun Path.exists() = FileSystem.SYSTEM.exists(this)

/**
 * Create the directory with all required parents at this [Path].
 */
internal fun Path.mkdirs() = FileSystem.SYSTEM.createDirectories(this)

/**
 * Read the content of the file at this [Path] as a UTF8 [String].
 */
internal fun Path.read() = FileSystem.SYSTEM.read(this) { readUtf8() }

/**
 * Write the UTF8 [content] to the file at this [Path].
 */
internal fun Path.write(content: String) = FileSystem.SYSTEM.write(this) { writeUtf8(content) }

/**
 * Write the content of the [channel] to this [Path].
 */
internal suspend fun Path.writeFromChannel(channel: ByteReadChannel) {
    FileSystem.SYSTEM.sink(this).buffer().use { sink ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (channel.readAvailable(buffer).also { bytesRead = it } > 0) {
            sink.write(buffer, 0, bytesRead)
        }
    }
}

/**
 * Get the user's home directory as a [Path].
 */
expect fun getHomeDirectory(): Path

/**
 * Set the permissions of the file at this [Path] to owner read and write only (0600).
 */
internal expect fun Path.setPermissionsToOwnerReadWrite()
