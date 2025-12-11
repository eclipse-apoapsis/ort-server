/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.config.local

import com.typesafe.config.Config

import java.io.File
import java.io.InputStream

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigFileProvider
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path

/**
 * An implementation of [ConfigFileProvider] that reads config files from a [local directory][configDir].
 */
class LocalConfigFileProvider(
    private val configDir: File
) : ConfigFileProvider {
    companion object {
        /**
         * Configuration property for the directory where config files are stored.
         */
        const val CONFIG_DIR = "localConfigDir"

        /**
         * Create a new instance of [LocalConfigFileProvider] that is initialized based on the given [config].
         */
        fun create(config: Config): LocalConfigFileProvider {
            val configDir = config.getString(CONFIG_DIR)
            val file = File(configDir)

            require(file.isDirectory) {
                "The configured path '$configDir' is not a directory."
            }

            return LocalConfigFileProvider(file)
        }
    }

    override fun resolveContext(context: Context) = ConfigManager.EMPTY_CONTEXT

    override fun getFile(context: Context, path: Path): InputStream =
        runCatching {
            configDir.resolve(path.path).inputStream()
        }.getOrElse {
            throw ConfigException("Cannot read path '${path.path}'.", it)
        }

    override fun contains(context: Context, path: Path): Boolean {
        val isDirectoryPath = path.path.endsWith("/")
        val p = configDir.resolve(path.path)

        return (!isDirectoryPath && p.isFile) || (isDirectoryPath && p.isDirectory)
    }

    override fun listFiles(context: Context, path: Path): Set<Path> {
        val dir = configDir.resolve(path.path)

        if (!dir.isDirectory) {
            throw ConfigException("The provided path '${path.path}' does not refer a directory.", null)
        }

        return dir.walk().maxDepth(1).filter { it.isFile }.mapTo(mutableSetOf()) { Path(it.path) }
    }
}
