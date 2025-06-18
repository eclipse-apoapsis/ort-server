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

package org.eclipse.apoapsis.ortserver.workers.common

import com.fasterxml.jackson.module.kotlin.readValue

import java.io.InputStream

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.PluginConfig
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

import org.ossreviewtoolkit.model.yamlMapper

import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger(ConfigManager::class.java)

/**
 * Map the entries of all [PluginConfig.options] in this map using the provided [transform] function.
 */
fun Map<String, PluginConfig>.mapOptions(
    transform: (Map.Entry<String, String>) -> String
): Map<String, PluginConfig> =
    mapValues { (_, pluginConfig) -> pluginConfig.copy(options = pluginConfig.options.mapValues(transform)) }

/**
 * If [path] is not `null` and does not equal the [defaultPath], read the YAML configuration file using the provided
 * [context] and deserialize its value. If a [ConfigException] occurs while reading the file it is rethrown. If [path]
 * is `null` or equals the [defaultPath], the file at [defaultPath] is read instead. If the file cannot be read, the
 * [fallbackValue] is returned.
 *
 * This function realizes the contract that if a specific config file is requested, not being able to read it leads to
 * an exception, but the default file is allowed to not exist. Not being able to deserialize the file to the return type
 * [T] always leads to an exception.
 */
inline fun <reified T> ConfigManager.readConfigFileValueWithDefault(
    path: String?,
    defaultPath: String,
    fallbackValue: T,
    context: Context?
): T = getConfigFileWithDefault<T>(path, defaultPath, fallbackValue, context, ::readConfigFileValue)

/**
 * If [path] is not `null` and does not equal the [defaultPath], read the file using the provided [context]. If a
 * [ConfigException] occurs while reading the file it is rethrown. If [path] is `null` or equals the [defaultPath], the
 * file at [defaultPath] is read instead. If the file cannot be read, the [fallbackValue] is returned.
 *
 * This function realizes the contract that if a specific config file is requested, not being able to read it leads to
 * an exception, but the default file is allowed to not exist.
 */
fun ConfigManager.readConfigFileWithDefault(
    path: String?,
    defaultPath: String,
    fallbackValue: String,
    context: Context?
): String = getConfigFileWithDefault(path, defaultPath, fallbackValue, context, ::readConfigFile)

/**
 * If [path] is not `null` and does not equal the [defaultPath], get the file using the provided [context] and
 * [getConfigFile] function. If a [ConfigException] occurs while reading the file it is rethrown. If [path] is `null`
 * or equals the [defaultPath], the file at [defaultPath] is read instead. If the file cannot be read, the
 * [fallbackValue] is returned.
 *
 * This function realizes the contract that if a specific config file is requested, not being able to read it leads to
 * an exception, but the default file is allowed to not exist.
 */
@PublishedApi
internal inline fun <reified T> getConfigFileWithDefault(
    path: String?,
    defaultPath: String,
    fallbackValue: T,
    context: Context?,
    getConfigFile: (path: String, context: Context?, exceptionHandler: (ConfigException) -> T) -> T
): T = if (path != null && path != defaultPath) {
    getConfigFile(path, context) {
        logger.error("Could not get config file from path '$path'.")
        throw it
    }
} else {
    getConfigFile(defaultPath, context) {
        logger.warn("Could not get config file from default path '$defaultPath', returning default value.")
        fallbackValue
    }
}

/**
 * Read the YAML configuration file at [path] using the provided [context] and deserialize its content. If a
 * [ConfigException] occurs while reading the file, the [exceptionHandler] is invoked which rethrows the exception by
 * default. If another exception occurs while reading the file, it is rethrown.
 */
inline fun <reified T> ConfigManager.readConfigFileValue(
    path: String,
    context: Context?,
    exceptionHandler: (ConfigException) -> T = { throw it }
): T = getConfigFile(path, context, { yamlMapper.readValue<T>(it) }, exceptionHandler)

/**
 * Read the configuration file at [path] using the provided [context]. If a [ConfigException] occurs while reading the
 * file, the [exceptionHandler] is invoked which rethrows the exception by default. If another exception occurs while
 * reading the file, it is rethrown.
 */
fun ConfigManager.readConfigFile(
    path: String,
    context: Context?,
    exceptionHandler: (ConfigException) -> String = { throw it }
): String = getConfigFile(path, context, { it.reader().readText() }, exceptionHandler)

/**
 * Get an [InputStream] for the configuration file at [path] using the provided [context] and pass it to the provided
 * [resultHandler]. If a [ConfigException] occurs while reading the file, the [exceptionHandler] is invoked which
 * rethrows the exception by default. If another exception occurs while reading the file, it is rethrown.
 */
inline fun <reified T> ConfigManager.getConfigFile(
    path: String,
    context: Context?,
    resultHandler: (InputStream) -> T,
    exceptionHandler: (ConfigException) -> T = { throw it }
): T = runCatching {
    resultHandler(getFile(context, Path(path)))
}.getOrElse {
    if (it is ConfigException) exceptionHandler(it) else throw it
}

/**
 * Return the resolved context for accessing configuration files from this [WorkerContext] if it is defined.
 */
val WorkerContext.resolvedConfigurationContext: Context?
    get() = ortRun.resolvedJobConfigContext?.let(::Context)
