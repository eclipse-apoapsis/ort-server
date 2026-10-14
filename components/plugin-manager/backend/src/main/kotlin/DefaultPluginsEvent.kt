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

import kotlin.time.Clock
import kotlin.time.Instant

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.model.OrganizationId

/**
 * An event changing the default plugins of a [category]. If [organizationId] is `null`, the event applies to the
 * global defaults, otherwise to the defaults of the organization.
 */
internal data class DefaultPluginsEvent(
    val organizationId: OrganizationId?,
    val category: DefaultPluginsCategory,
    val version: Long,
    val payload: DefaultPluginsEventPayload,
    val createdBy: String,
    val createdAt: Instant = Clock.System.now()
)

/** The base class for all default plugins event payloads. */
@Serializable
internal sealed interface DefaultPluginsEventPayload

/** The payload for an event that sets the default plugins to the given ordered list of [pluginIds]. */
@Serializable
@SerialName("DefaultPluginsSet")
internal class DefaultPluginsSet(
    val pluginIds: List<String>
) : DefaultPluginsEventPayload

/**
 * The payload for an event that resets the default plugins, so that the defaults from the next higher level are used.
 */
@Serializable
@SerialName("DefaultPluginsReset")
internal object DefaultPluginsReset : DefaultPluginsEventPayload
