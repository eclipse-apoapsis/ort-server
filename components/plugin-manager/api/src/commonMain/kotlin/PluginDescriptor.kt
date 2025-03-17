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

import kotlinx.serialization.Serializable

@Serializable
/**
 * A descriptor holding the metadata of a plugin.
 */
data class PluginDescriptor(
    val id: String,
    val type: PluginType,
    val displayName: String,
    val description: String,
    val options: List<PluginOption> = emptyList()
)

/**
 * The supported types of plugins.
 */
enum class PluginType {
    ADVISOR,
    PACKAGE_CONFIGURATION_PROVIDER,
    PACKAGE_CURATION_PROVIDER,
    PACKAGE_MANAGER,
    REPORTER,
    SCANNER
}

/**
 * A configuration option for a plugin.
 */
@Serializable
data class PluginOption(
    val name: String,
    val description: String,
    val type: PluginOptionType,
    val defaultValue: String?,
    val isNullable: Boolean,
    val isRequired: Boolean
)

/**
 * The supported types of plugin options.
 */
enum class PluginOptionType {
    BOOLEAN,
    INTEGER,
    LONG,
    SECRET,
    STRING,
    STRING_LIST
}
