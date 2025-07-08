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

/** The current state of a plugin template based on the [applied][apply] events. */
internal class PluginTemplateState(
    val name: String,
    val pluginType: PluginType,
    val pluginId: String
) {
    var options: List<PluginOptionTemplate> = emptyList()
        private set

    var isGlobal: Boolean = false
        private set

    var organizationIds: List<Long> = emptyList()
        private set

    var isDeleted: Boolean = false
        private set

    var version: Long = 0
        private set

    fun apply(event: PluginTemplateEvent) = apply {
        when (event.payload) {
            is Deleted -> isDeleted = true
            is GlobalDisabled -> isGlobal = false
            is GlobalEnabled -> isGlobal = true
            is OptionsUpdated -> options = event.payload.options
            is OrganizationAdded -> organizationIds += event.payload.organizationId
            is OrganizationRemoved -> organizationIds -= event.payload.organizationId
        }

        version = event.version
    }

    fun applyAll(events: List<PluginTemplateEvent>) = apply {
        events.forEach { apply(it) }
    }
}
