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

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal data class PluginTemplateEvent(
    val name: String,
    val pluginType: PluginType,
    val pluginId: String,
    val version: Long,
    val payload: PluginTemplateEventPayload,
    val createdBy: String,
    val createdAt: Instant = Clock.System.now()
)

@Serializable
internal sealed interface PluginTemplateEventPayload

@Serializable
@SerialName("Deleted")
internal object Deleted : PluginTemplateEventPayload

@Serializable
@SerialName("GlobalDisabled")
internal object GlobalDisabled : PluginTemplateEventPayload

@Serializable
@SerialName("GlobalEnabled")
internal object GlobalEnabled : PluginTemplateEventPayload

@Serializable
@SerialName("OptionsUpdated")
internal class OptionsUpdated(
    val options: List<PluginOptionTemplate>
) : PluginTemplateEventPayload

@Serializable
@SerialName("OrganizationAdded")
internal class OrganizationAdded(
    val organizationId: Long
) : PluginTemplateEventPayload

@Serializable
@SerialName("OrganizationRemoved")
internal class OrganizationRemoved(
    val organizationId: Long
) : PluginTemplateEventPayload
