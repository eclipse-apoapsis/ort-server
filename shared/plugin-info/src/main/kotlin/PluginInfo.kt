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

package org.eclipse.apoapsis.ortserver.shared.plugininfo

import org.slf4j.LoggerFactory

/**
 * An object providing information about the ORT plugins that are available in ORT Server.
 *
 * This object uses information that has been generated already at build time. This information is therefore available
 * from everywhere without the need to depend on all modules that provide plugins.
 *
 * TODO: Provide further information about plugins beyond their IDs.
 */
object PluginInfo {
    /** The name of the classpath resource that contains the plugin summary. */
    private const val PLUGIN_SUMMARY_RESOURCE = "/plugin_summary.csv"

    private val logger = LoggerFactory.getLogger(javaClass)

    /** A map storing information about all available ORT plugins. */
    private val pluginInfos = loadPluginInfo()

    /**
     * A set with the IDs of all ORT plugins that are available in ORT Server.
     */
    val pluginIds: Set<TypedPluginId>
        get() = pluginInfos.values.flatten().toSet()

    /** A set with all known types for plugins. */
    val pluginTypes: Set<PluginType>
        get() = pluginInfos.keys

    /**
     * Return a list of all [TypedPluginId]s for the given [type]. Throw an exception if the type is unknown.
     */
    fun pluginsForType(type: PluginType): List<TypedPluginId> =
        pluginInfos.getValue(type)

    /**
     * Load the file with plugin information from the classpath and parse it. Return a map that groups the plugins by
     * their type.
     */
    private fun loadPluginInfo(): Map<PluginType, List<TypedPluginId>> =
        javaClass.getResourceAsStream(PLUGIN_SUMMARY_RESOURCE)?.use { inputStream ->
            inputStream.bufferedReader().use { reader ->
                reader.readLines().mapNotNullTo(mutableSetOf()) { line ->
                    line.split(",").takeIf { it.size == 3 }?.let { parts ->
                        TypedPluginId(
                            PluginId(parts[0]),
                            PluginType(parts[1])
                        )
                    }
                }
            }
        }?.groupBy { it.type }?.also {
            logger.info("Loaded plugin summary from resource '$PLUGIN_SUMMARY_RESOURCE': ${it.size} plugins found.")
            logger.debug("The following plugin IDs were found: {}", it)
        } ?: error("Could not load plugin summary from resource '$PLUGIN_SUMMARY_RESOURCE'.")
}
