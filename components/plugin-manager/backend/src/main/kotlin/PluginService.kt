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

package org.eclipse.apoapsis.ortserver.components.pluginmanager

import org.eclipse.apoapsis.ortserver.dao.blockingQuery

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * A service to query ORT plugins and their configuration.
 */
class PluginService(private val db: Database) {
    /**
     * Returns the [PluginAvailability] of the plugin with the given [pluginType] and [pluginId]. Returns
     * [PluginAvailability.DISABLED] if the plugin is not installed.
     */
    fun getAvailability(pluginType: PluginType, pluginId: String): PluginAvailability {
        val normalizedPluginId = normalizePluginId(pluginType, pluginId) ?: return PluginAvailability.DISABLED

        return db.blockingQuery {
            PluginsReadModel.select(PluginsReadModel.availability)
                .where {
                    PluginsReadModel.pluginType eq pluginType and
                            (PluginsReadModel.pluginId eq normalizedPluginId)
                }
                .firstOrNull()?.get(PluginsReadModel.availability) ?: PluginAvailability.ENABLED
        }
    }

    /**
     * Returns `true` if the plugin with the given [pluginType] and [pluginId] is installed, `false` otherwise.
     */
    fun isInstalled(pluginType: PluginType, pluginId: String) = normalizePluginId(pluginType, pluginId) != null

    /**
     * Returns the [PluginDescriptor]s for all installed ORT plugins.
     */
    fun getPlugins(): List<PluginDescriptor> {
        val pluginInfo = mutableMapOf<PluginType, MutableMap<String, PluginAvailability>>()

        db.blockingQuery {
            PluginsReadModel.selectAll().forEach {
                val pluginType = it[PluginsReadModel.pluginType]
                val pluginId = it[PluginsReadModel.pluginId]
                val availability = it[PluginsReadModel.availability]

                pluginInfo.getOrPut(pluginType) { mutableMapOf() }[pluginId] = availability
            }
        }

        fun pluginAvailability(pluginType: PluginType, pluginId: String) =
            pluginInfo[pluginType]?.get(pluginId) ?: PluginAvailability.ENABLED

        val advisors = getInstalledPlugins(PluginType.ADVISOR).map {
            it.mapToApi(PluginType.ADVISOR, pluginAvailability(PluginType.ADVISOR, it.id))
        }

        val packageConfigurationProviders = getInstalledPlugins(PluginType.PACKAGE_CONFIGURATION_PROVIDER).map {
            it.mapToApi(
                PluginType.PACKAGE_CONFIGURATION_PROVIDER,
                pluginAvailability(PluginType.PACKAGE_CONFIGURATION_PROVIDER, it.id)
            )
        }

        val packageCurationProviders = getInstalledPlugins(PluginType.PACKAGE_CURATION_PROVIDER).map {
            it.mapToApi(
                PluginType.PACKAGE_CURATION_PROVIDER,
                pluginAvailability(PluginType.PACKAGE_CURATION_PROVIDER, it.id)
            )
        }

        val packageManagers = getInstalledPlugins(PluginType.PACKAGE_MANAGER).map {
            it.mapToApi(PluginType.PACKAGE_MANAGER, pluginAvailability(PluginType.PACKAGE_MANAGER, it.id))
        }

        val reporters = getInstalledPlugins(PluginType.REPORTER).map {
            it.mapToApi(PluginType.REPORTER, pluginAvailability(PluginType.REPORTER, it.id))
        }

        val scanners = getInstalledPlugins(PluginType.SCANNER).map {
            it.mapToApi(PluginType.SCANNER, pluginAvailability(PluginType.SCANNER, it.id))
        }

        return advisors +
                packageConfigurationProviders +
                packageCurationProviders +
                packageManagers +
                reporters +
                scanners
    }
}
