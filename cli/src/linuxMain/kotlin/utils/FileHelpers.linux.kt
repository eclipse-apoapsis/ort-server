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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString

import okio.Path
import okio.Path.Companion.toPath

import org.eclipse.apoapsis.ortserver.cli.getEnv

import platform.posix.S_IRUSR as USER_READ
import platform.posix.S_IWUSR as USER_WRITE
import platform.posix.chmod
import platform.posix.mode_t

@OptIn(ExperimentalForeignApi::class)
internal actual fun Path.setPermissionsToOwnerReadWrite() {
    chmod(toString(), (USER_READ or USER_WRITE).convert<mode_t>())
}

@OptIn(ExperimentalForeignApi::class)
actual fun getHomeDirectory(): Path = requireNotNull(
    getEnv("HOME")?.toPath() ?: platform.posix.getpwuid(platform.posix.getuid())?.pointed?.pw_dir?.toKString()?.toPath()
) {
    "Could not determine the home directory."
}
