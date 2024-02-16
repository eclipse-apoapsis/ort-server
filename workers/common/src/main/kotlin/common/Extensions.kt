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

package org.ossreviewtoolkit.server.workers.common

import com.fasterxml.jackson.module.kotlin.readValue

import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.server.config.ConfigException
import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Context
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext

import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger(ConfigManager::class.java)

/**
 * If [path] is not `null`, read the file using the provided [context]. If a [ConfigException] occurs while reading the
 * file it is rethrown. If [path] is `null`, the file at [defaultPath] is read instead. If the file cannot be read, the
 * [fallbackValue] is returned.
 * This function realizes the contract that if a specific config file is requested, not being able to read it leads to
 * an exception, but the default file is allowed to not exist. Not being able to deserialize the file to the return type
 * [T] always leads to an exception.
 */
inline fun <reified T> ConfigManager.readConfigFileWithDefault(
    path: String?,
    defaultPath: String,
    fallbackValue: T,
    context: Context?
): T = if (path != null) {
    readConfigFile(path, context) {
        logger.error("Could not read config file from path '$path'.")
        throw it
    }
} else {
    readConfigFile(defaultPath, context) {
        logger.warn("Could not read config file from default path '$defaultPath'.")
        fallbackValue
    }
}

/**
 * Read the YAML configuration file at [path] using the provided [context]. If a [ConfigException] occurs while reading
 * the file, the [exceptionHandler] is invoked which rethrows the exception by default. If another exception occurs
 * while reading the file it is rethrown.
 */
inline fun <reified T> ConfigManager.readConfigFile(
    path: String,
    context: Context?,
    exceptionHandler: (ConfigException) -> T = { throw it }
): T = runCatching {
    getFile(context, Path(path)).use {
        yamlMapper.readValue<T>(it)
    }
}.getOrElse {
    if (it is ConfigException) exceptionHandler(it) else throw it
}

/**
 * Return the resolved context for accessing configuration files from this [WorkerContext] if it is defined.
 */
val WorkerContext.resolvedConfigurationContext: Context?
    get() = ortRun.resolvedJobConfigContext?.let(::Context)
