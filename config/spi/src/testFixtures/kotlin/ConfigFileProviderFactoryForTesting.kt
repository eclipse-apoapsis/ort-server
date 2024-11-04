/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import com.typesafe.config.Config

import java.io.File
import java.io.InputStream

import kotlin.IllegalArgumentException

import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull

/**
 * A test implementation of the [ConfigFileProvider] interface. This implementation interprets the context as an
 * (absolute) path to a directory. Paths to configuration files are then resolved relatively to this root path.
 */
class ConfigFileProviderFactoryForTesting : ConfigFileProviderFactory {
    companion object {
        /** The name of this test implementation. */
        const val NAME = "configFileProviderForTesting"

        /**
         * Name of a configuration property that simulates a secret required by this implementation. If it is defined,
         * this implementation will query the value from the provided [ConfigManager]. This is used to test whether
         * access to secrets is possible when a config file provider is created.
         */
        const val SECRET_PROPERTY = "secret"

        /** A prefix to mark a context as resolved. */
        const val RESOLVED_PREFIX = "resolved://"

        /**
         * Constant for a special value that causes the config file manager implementation to throw an exception when
         * used as [Path] or [Context]. This can be used in tests for exception handling.
         */
        const val ERROR_VALUE = "errorConfig"

        /**
         * Test whether secrets can be queried from the given [secretProvider] if this check is enabled.
         */
        private fun checkConfigManagerSecretAccess(config: Config, secretProvider: ConfigSecretProvider) {
            config.getStringOrNull(SECRET_PROPERTY)?.let { secret ->
                secretProvider.getSecret(Path(secret))
            }
        }
    }

    override val name: String = NAME

    override fun createProvider(config: Config, secretProvider: ConfigSecretProvider): ConfigFileProvider {
        checkConfigManagerSecretAccess(config, secretProvider)

        fun configRoot(context: Context): File =
            if (context == ConfigManager.EMPTY_CONTEXT) {
                File("src/testFixtures/resources/config-files")
            } else {
                File(context.name)
            }

        fun resolveFile(context: Context, path: Path): File =
            path.takeUnless { it.path == ERROR_VALUE }?.let { configRoot(context).resolve(it.path) }
                ?: throw IllegalArgumentException("Error when accessing path.")

        return object : ConfigFileProvider {
            override fun resolveContext(context: Context): Context =
                context.takeUnless { it.name == ERROR_VALUE }?.let {
                    Context("$RESOLVED_PREFIX${it.name}")
                } ?: throw IllegalArgumentException("Error context.")

            override fun getFile(context: Context, path: Path): InputStream =
                resolveFile(context, path).inputStream()

            override fun contains(context: Context, path: Path): Boolean =
                resolveFile(context, path).isFile

            override fun listFiles(context: Context, path: Path): Set<Path> {
                return resolveFile(context, path).list()?.mapTo(mutableSetOf()) { name ->
                    Path("${path.path}/$name")
                } ?: throw IllegalArgumentException("Invalid path to list: '$path'.")
            }
        }
    }
}
