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

package org.eclipse.apoapsis.ortserver.core.utils

import org.eclipse.apoapsis.ortserver.api.v1.model.PostRepositoryRun
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType

/**
 * Return error messages for all plugin configs in this [PostRepositoryRun] whose keys differ only in case. As plugin
 * IDs are case-insensitive, such configs are ambiguous: Validation, the application of plugin templates, and the
 * workers could otherwise use different configs for the same plugin, which would allow bypassing plugin options that
 * are fixed by the server administrators.
 */
fun PostRepositoryRun.getAmbiguousPluginConfigErrors(): List<String> =
    mapOf(
        PluginType.ADVISOR to jobConfigs.advisor?.config?.keys,
        PluginType.PACKAGE_MANAGER to jobConfigs.analyzer.packageManagerOptions?.keys,
        PluginType.REPORTER to jobConfigs.reporter?.config?.keys,
        PluginType.SCANNER to jobConfigs.scanner?.config?.keys
    ).flatMap { (pluginType, pluginIds) ->
        // Group the plugin IDs the same way as they are matched when looking up plugins, which is not equivalent to
        // comparing their lowercase representations for some Unicode characters.
        val pluginIdsIgnoringCase = pluginIds.orEmpty().groupByTo(sortedMapOf(String.CASE_INSENSITIVE_ORDER)) { it }

        pluginIdsIgnoringCase.values.filter { it.size > 1 }.map { spellings ->
            "The configuration for the plugin with type '$pluginType' and ID '${spellings.first()}' is defined " +
                    "multiple times with different spellings of the plugin ID: ${spellings.joinToString { "'$it'" }}."
        }
    }
