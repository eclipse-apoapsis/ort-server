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
     * Returns `true` if the plugin with the given [type] and [id] is enabled, `false` otherwise.
     */
    fun isEnabled(type: PluginType, id: String) = db.transaction {
        PluginsReadModel.select(PluginsReadModel.enabled)
            .where { PluginsReadModel.pluginType eq type and (PluginsReadModel.pluginId eq id) }
            .firstOrNull()?.get(PluginsReadModel.enabled) ?: true
    }

    /**
     * Returns the [PluginDescriptor]s for all installed ORT plugins.
     */
    fun getPlugins(): List<PluginDescriptor> {
        val advisors = AdviceProviderFactory.ALL.values.map {
            it.descriptor.mapToApi(PluginType.ADVISOR, isEnabled(PluginType.ADVISOR, it.descriptor.id))
        }

        val packageConfigurationProviders = PackageConfigurationProviderFactory.ALL.values.map {
            it.descriptor.mapToApi(
                PluginType.PACKAGE_CONFIGURATION_PROVIDER,
                isEnabled(PluginType.PACKAGE_CONFIGURATION_PROVIDER, it.descriptor.id)
            )
        }

        val packageCurationProviders = PackageCurationProviderFactory.ALL.values.map {
            it.descriptor.mapToApi(
                PluginType.PACKAGE_CURATION_PROVIDER,
                isEnabled(PluginType.PACKAGE_CURATION_PROVIDER, it.descriptor.id)
            )
        }

        val packageManagers = PackageManagerFactory.ALL.values.map {
            it.descriptor.mapToApi(PluginType.PACKAGE_MANAGER, isEnabled(PluginType.PACKAGE_MANAGER, it.descriptor.id))
        }

        val reporters = ReporterFactory.ALL.values.map {
            it.descriptor.mapToApi(PluginType.REPORTER, isEnabled(PluginType.REPORTER, it.descriptor.id))
        }

        val scanners = ScannerWrapperFactory.ALL.values.map {
            it.descriptor.mapToApi(PluginType.SCANNER, isEnabled(PluginType.SCANNER, it.descriptor.id))
        }

        return advisors +
                packageConfigurationProviders +
                packageCurationProviders +
                packageManagers +
                reporters +
                scanners
    }
}
