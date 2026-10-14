/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import org.eclipse.apoapsis.ortserver.components.adminconfig.ReportDefinition

/**
 * Add the given [reportDefinitions], keyed by their names, as reporters to these available plugins. A report
 * definition is only added if the reporter it uses is available, and it gets the availability of this reporter. If
 * [replaceReporters] is `true`, the reporters used by the added report definitions are removed, because report
 * definitions replace the reporters they use.
 */
internal fun List<AvailablePlugin>.withReportDefinitions(
    reportDefinitions: Map<String, ReportDefinition>,
    replaceReporters: Boolean = true
): List<AvailablePlugin> {
    val reporters = filter { it.plugin.type == PluginType.REPORTER }.associateBy { it.plugin.id.lowercase() }

    val pluginsFromReportDefinitions = reportDefinitions.mapNotNull { (name, definition) ->
        val reporter = reporters[definition.pluginId.lowercase()] ?: return@mapNotNull null

        AvailablePlugin(
            plugin = PluginDescriptor(
                id = name,
                type = PluginType.REPORTER,
                displayName = name,
                summary = definition.description,
                description = null,
                availability = reporter.plugin.availability
            ),
            template = null
        )
    }

    val replacedReporterIds = if (replaceReporters) {
        reportDefinitions.values.mapNotNullTo(mutableSetOf()) { reporters[it.pluginId.lowercase()]?.plugin?.id }
    } else {
        emptySet()
    }

    return filterNot { it.plugin.type == PluginType.REPORTER && it.plugin.id in replacedReporterIds } +
            pluginsFromReportDefinitions
}
