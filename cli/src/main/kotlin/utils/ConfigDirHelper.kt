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

import org.eclipse.apoapsis.ortserver.cli.COMMAND_NAME

import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.safeMkdirs

/**
 * The directory where the configuration files are stored, respecting the XDG Base Directory specification [1].
 *
 * [1]: https://specifications.freedesktop.org/basedir-spec/latest/
 */
internal val configDir: File
    get() {
        val fallbackDir = Os.userHomeDirectory.resolve(".config/$COMMAND_NAME")

        val dir = when {
            Os.isLinux || Os.isMac -> Os.env["XDG_CONFIG_HOME"]?.let { File(it).resolve(COMMAND_NAME) } ?: fallbackDir

            Os.isWindows -> Os.env["XDG_CONFIG_HOME"]?.let { File(it) }
                ?: Os.env["LOCALAPPDATA"]?.let { File(it) }
                ?: fallbackDir

            else -> fallbackDir
        }

        if (!dir.exists()) dir.safeMkdirs()

        return dir
    }
