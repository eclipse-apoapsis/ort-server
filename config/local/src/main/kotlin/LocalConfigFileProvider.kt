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

import kotlin.coroutines.cancellation.CancellationException

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigFileProvider
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.config.RequestedConfigContext
import org.eclipse.apoapsis.ortserver.config.ResolvedConfigContext
import org.eclipse.apoapsis.ortserver.config.resolveSecurely
import org.eclipse.apoapsis.ortserver.shared.coroutines.Virtual
import org.eclipse.apoapsis.ortserver.shared.coroutines.withContextClosingOnCancel

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

    override suspend fun resolveContext(context: RequestedConfigContext) = ResolvedConfigContext.EMPTY

    override suspend fun getFile(context: ResolvedConfigContext, path: Path): InputStream =
        withContextClosingOnCancel(Dispatchers.Virtual) {
            runCatching {
                configDir.resolveSecurely(path).inputStream()
            }.getOrElse {
                if (it is CancellationException) throw it

                throw ConfigException("Cannot read path '${path.path}'.", it)
            }
        }

    override suspend fun contains(context: ResolvedConfigContext, path: Path): Boolean =
        withContext(Dispatchers.Virtual) {
            val isDirectoryPath = path.path.endsWith("/")
            val p = configDir.resolveSecurely(path)

            (!isDirectoryPath && p.isFile) || (isDirectoryPath && p.isDirectory)
        }

    override suspend fun listFiles(context: ResolvedConfigContext, path: Path): Set<Path> =
        withContext(Dispatchers.Virtual) {
            val requestedDir = configDir.resolve(path.path)
            val dir = configDir.resolveSecurely(path)

            if (!dir.isDirectory) {
                throw ConfigException("The provided path '${path.path}' does not refer a directory.")
            }

            dir.walk().maxDepth(1).filter { it.isFile }.mapTo(mutableSetOf()) {
                Path(requestedDir.resolve(it.relativeTo(dir)).path)
            }
        }
}
