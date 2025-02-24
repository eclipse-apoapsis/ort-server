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

import okio.Path
import okio.Path.Companion.toPath

import org.eclipse.apoapsis.ortserver.cli.getEnv

import platform.posix.S_IRUSR as READ_USER
import platform.posix.S_IWUSR as WRITE_USER
import platform.posix.chmod

actual fun getHomeDirectory(): Path = requireNotNull(getEnv("USERPROFILE")?.toPath() ?: getEnv("HOME")?.toPath()) {
    "Could not determine the home directory."
}

internal actual fun Path.setPermissionsToOwnerReadWrite() {
    chmod(toString(), READ_USER or WRITE_USER)
}
