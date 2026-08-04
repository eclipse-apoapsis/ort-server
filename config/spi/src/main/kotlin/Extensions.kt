/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.config

import java.io.File

/**
 * Resolve the given [path] against this base directory and ensure that the result is contained within this directory.
 *
 * This functions must be used by file-based [ConfigFileProvider] implementations that resolve user-controllable [Path]s
 * to ensure that they do not resolve files outside the config directory.
 *
 * Return the canonical [File] the [path] resolves to. Throw a [ConfigException] if the resolved target escapes this
 * directory (for example, because [path] is absolute or contains `..` segments that traverse above this directory).
 */
fun File.resolveSecurely(path: Path): File {
    val rootPath = canonicalFile.toPath()
    val target = resolve(path.path).canonicalFile
    val targetPath = target.toPath()

    if (targetPath != rootPath && !targetPath.startsWith(rootPath)) {
        throw ConfigException("The path '${path.path}' escapes the configured base directory.")
    }

    return target
}
