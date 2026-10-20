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

package org.eclipse.apoapsis.ortserver.components.pluginmanager.queries

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplatesReadModel

import org.jetbrains.exposed.v1.core.ResultRow

/** Convert this row of the [PluginTemplatesReadModel] to a [PluginTemplate]. */
internal fun ResultRow.toPluginTemplate() =
    PluginTemplate(
        name = this[PluginTemplatesReadModel.name],
        pluginType = this[PluginTemplatesReadModel.pluginType],
        pluginId = this[PluginTemplatesReadModel.pluginId],
        options = this[PluginTemplatesReadModel.pluginOptions],
        isGlobal = this[PluginTemplatesReadModel.isGlobal],
        organizationIds = this[PluginTemplatesReadModel.organizationIds]
    )
