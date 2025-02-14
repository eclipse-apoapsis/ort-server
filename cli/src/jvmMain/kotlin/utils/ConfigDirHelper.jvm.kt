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

import java.io.File

import okio.Path
import okio.Path.Companion.toOkioPath

import org.eclipse.apoapsis.ortserver.cli.COMMAND_NAME

internal actual val configDir: Path
    get() {
        val osName = System.getProperty("os.name").lowercase()
        val fallbackDir = File(fixupUserHomeProperty()).resolve(".config/$COMMAND_NAME")

        val dir = when {
            osName.contains("linux") || osName.contains("mac") ->
                System.getenv("XDG_CONFIG_HOME")?.let { File(it).resolve(COMMAND_NAME) }
                    ?: fallbackDir

            osName.contains("windows") -> System.getenv("XDG_CONFIG_HOME")?.let { File(it) }
                ?: System.getenv("LOCALAPPDATA")?.let { File(it) } ?: fallbackDir

            else -> fallbackDir
        }

        if (!dir.exists()) dir.mkdirs()

        return dir.toOkioPath()
    }
