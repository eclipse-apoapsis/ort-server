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

package org.eclipse.apoapsis.ortserver.core.utils

import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType

/**
 * Returns all globally disabled plugins used in the [CreateOrtRun] object.
 */
fun CreateOrtRun.getDisabledPlugins(pluginService: PluginService): List<Pair<PluginType, String>> =
    buildList {
        getPlugins().forEach { (pluginType, pluginIds) ->
            pluginIds.forEach { pluginId ->
                if (!pluginService.isEnabled(pluginType, pluginId)) {
                    add(pluginType to pluginId)
                }
            }
        }
    }

/**
 * Returns all plugins configured in the [CreateOrtRun] object.
 */
private fun CreateOrtRun.getPlugins(): Map<PluginType, Set<String>> =
    buildMap {
        val advisors = jobConfigs.advisor?.advisors?.toSet().orEmpty()
        put(PluginType.ADVISOR, advisors)

        val packageConfigurationProviders = buildSet {
            addAll(jobConfigs.evaluator?.packageConfigurationProviders?.map { it.type }.orEmpty())
            addAll(jobConfigs.reporter?.packageConfigurationProviders?.map { it.type }.orEmpty())
        }
        put(PluginType.PACKAGE_CONFIGURATION_PROVIDER, packageConfigurationProviders)

        val packageCurationProviders =
            jobConfigs.analyzer.packageCurationProviders?.mapTo(mutableSetOf()) { it.type }.orEmpty()
        put(PluginType.PACKAGE_CURATION_PROVIDER, packageCurationProviders)

        val packageManagers = jobConfigs.analyzer.enabledPackageManagers?.toSet().orEmpty()
        put(PluginType.PACKAGE_MANAGER, packageManagers)

        val reporters = jobConfigs.reporter?.formats?.toSet().orEmpty()
        put(PluginType.REPORTER, reporters)

        val scanners = buildSet {
            addAll(jobConfigs.scanner?.scanners.orEmpty())
            addAll(jobConfigs.scanner?.projectScanners.orEmpty())
        }
        put(PluginType.SCANNER, scanners)
    }
