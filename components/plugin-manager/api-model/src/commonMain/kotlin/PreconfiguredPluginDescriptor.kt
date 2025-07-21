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

/** A [PluginDescriptor] that is preconfigured with the [PluginTemplate]s configured for the organization. */
@Serializable
data class PreconfiguredPluginDescriptor(
    /** The ID of the plugin. */
    val id: String,

    /** The type of the plugin. */
    val type: PluginType,

    /** The display name of the plugin. */
    val displayName: String,

    /** The description of the plugin. */
    val description: String,

    /** The configuration options of the plugin. */
    val options: List<PreconfiguredPluginOption>
)

/** A [PluginOption] that is preconfigured with the [PluginOptionTemplate]s configured for the organization. */
@Serializable
data class PreconfiguredPluginOption(
    /** The name of the option. */
    val name: String,

    /** The description of the option. */
    val description: String,

    /** The type of the option. */
    val type: PluginOptionType,

    /** The default value of the option, if any. */
    val defaultValue: String?,

    /** Whether the option is fixed and cannot be changed by the user. */
    val isFixed: Boolean,

    /** Whether the option can be set to `null`. */
    val isNullable: Boolean,

    /** Whether the option is required and must be set. */
    val isRequired: Boolean
)
