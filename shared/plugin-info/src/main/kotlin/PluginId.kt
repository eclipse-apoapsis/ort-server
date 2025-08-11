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

/**
 * A value class to represent a plugin ID.
 *
 * A plugin ID is a human-readable alphanumeric identifier. Note that the ID alone is not necessarily sufficient to
 * uniquely identify a plugin, since it is possible that plugins of different types share the same ID.
 */
@JvmInline
value class PluginId(val id: String)

/**
 * A data class to represent the type of a plugin. The type is derived from the fully-qualified name of the plugin's
 * factory class.
 */
@JvmInline
value class PluginType(val type: String)

/**
 * A data class that uniquely identifies a plugin by combining its [PluginId] and [PluginType].
 */
data class TypedPluginId(
    /** The ID of the plugin. */
    val id: PluginId,

    /** The type of the plugin. */
    val type: PluginType
)
