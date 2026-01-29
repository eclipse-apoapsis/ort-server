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

import org.ossreviewtoolkit.plugins.api.PluginDescriptor as OrtPluginDescriptor
import org.ossreviewtoolkit.plugins.api.PluginOption as OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginOptionType as OrtPluginOptionType

internal fun OrtPluginDescriptor.mapToApi(type: PluginType, enabled: Boolean) = PluginDescriptor(
    id = id,
    type = type,
    displayName = displayName,
    description = description,
    options = options.map { it.mapToApi() },
    enabled = enabled
)

internal fun OrtPluginOption.mapToApi() = PluginOption(
    name = name,
    description = description,
    type = type.mapToApi(),
    defaultValue = defaultValue,
    isNullable = isNullable,
    isRequired = isRequired
)

internal fun OrtPluginOptionType.mapToApi() = when (this) {
    OrtPluginOptionType.BOOLEAN -> PluginOptionType.BOOLEAN
    OrtPluginOptionType.ENUM -> PluginOptionType.ENUM
    OrtPluginOptionType.ENUM_LIST -> PluginOptionType.ENUM_LIST
    OrtPluginOptionType.INTEGER -> PluginOptionType.INTEGER
    OrtPluginOptionType.LONG -> PluginOptionType.LONG
    OrtPluginOptionType.SECRET -> PluginOptionType.SECRET
    OrtPluginOptionType.STRING -> PluginOptionType.STRING
    OrtPluginOptionType.STRING_LIST -> PluginOptionType.STRING_LIST
}
