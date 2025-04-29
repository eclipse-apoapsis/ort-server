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

import org.eclipse.apoapsis.ortserver.components.pluginmanager.endpoints.mapToApi

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.utils.DatabaseUtils.transaction
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory

/**
 * A service to query ORT plugins and their configuration.
 */
class PluginService(private val db: Database) {
    /**
     * Returns `true` if the plugin with the given [pluginType] and [pluginId] is enabled, `false` otherwise.
     */
    fun isEnabled(pluginType: PluginType, pluginId: String) = db.transaction {
        PluginsReadModel.select(PluginsReadModel.enabled)
            .where { PluginsReadModel.pluginType eq pluginType and (PluginsReadModel.pluginId eq pluginId) }
            .firstOrNull()?.get(PluginsReadModel.enabled) ?: true
    }

    /**
     * Returns the [PluginDescriptor]s for all installed ORT plugins.
     */
    fun getPlugins(): List<PluginDescriptor> {
        val pluginInfo = mutableMapOf<PluginType, MutableMap<String, Boolean>>()

        db.transaction {
            PluginsReadModel.selectAll().forEach {
                val pluginType = it[PluginsReadModel.pluginType]
                val pluginId = it[PluginsReadModel.pluginId]
                val enabled = it[PluginsReadModel.enabled]

                pluginInfo.getOrPut(pluginType) { mutableMapOf() }[pluginId] = enabled
            }
        }

        fun isPluginEnabled(pluginType: PluginType, pluginId: String) = pluginInfo[pluginType]?.get(pluginId) ?: true

        val advisors = AdviceProviderFactory.ALL.values.map {
            it.descriptor.mapToApi(PluginType.ADVISOR, isPluginEnabled(PluginType.ADVISOR, it.descriptor.id))
        }

        val packageConfigurationProviders = PackageConfigurationProviderFactory.ALL.values.map {
            it.descriptor.mapToApi(
                PluginType.PACKAGE_CONFIGURATION_PROVIDER,
                isPluginEnabled(PluginType.PACKAGE_CONFIGURATION_PROVIDER, it.descriptor.id)
            )
        }

        val packageCurationProviders = PackageCurationProviderFactory.ALL.values.map {
            it.descriptor.mapToApi(
                PluginType.PACKAGE_CURATION_PROVIDER,
                isPluginEnabled(PluginType.PACKAGE_CURATION_PROVIDER, it.descriptor.id)
            )
        }

        val packageManagers = PackageManagerFactory.ALL.values.map {
            it.descriptor.mapToApi(
                PluginType.PACKAGE_MANAGER,
                isPluginEnabled(PluginType.PACKAGE_MANAGER, it.descriptor.id)
            )
        }

        val reporters = ReporterFactory.ALL.values.map {
            it.descriptor.mapToApi(PluginType.REPORTER, isPluginEnabled(PluginType.REPORTER, it.descriptor.id))
        }

        val scanners = ScannerWrapperFactory.ALL.values.map {
            it.descriptor.mapToApi(PluginType.SCANNER, isPluginEnabled(PluginType.SCANNER, it.descriptor.id))
        }

        return advisors +
                packageConfigurationProviders +
                packageCurationProviders +
                packageManagers +
                reporters +
                scanners
    }
}
