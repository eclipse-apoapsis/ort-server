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

/**
 * A template for a plugin config. This can be used by admins to pre-configure plugins, to configure which plugin
 * options can be set by users, and to configure different defaults for different organizations.
 */
@Serializable
data class PluginTemplate(
    /** The name of the template. */
    val name: String,

    /** The type of the plugin. */
    val pluginType: PluginType,

    /** The plugin ID. */
    val pluginId: String,

    /** The config templates for plugin options. */
    val options: List<PluginOptionTemplate>,

    /** Whether the template is global and applies to all organizations. */
    val isGlobal: Boolean,

    /** The list of organization IDs this template applies to. If [isGlobal] is `true`, this property is ignored. */
    val organizationIds: List<Long> = emptyList()
)

/**
 * A template config for a specific plugin option.
 */
@Serializable
data class PluginOptionTemplate(
    /** The name of the option. */
    val option: String,

    /** The type of the plugin option. */
    val type: PluginOptionType,

    /**
     * The value of the option. It will be cast to the option [type]. If `null`, the plugin option will be set to `null`
     * if it is nullable, otherwise this will cause an error.
     */
    val value: String?,

    /** Whether the value can be overwritten by users. */
    val isFinal: Boolean
)
