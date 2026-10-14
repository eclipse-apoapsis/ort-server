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

/** The current state of the default plugins of one scope and category based on the [applied][apply] events. */
internal class DefaultPluginsState {
    /** The ordered IDs of the default plugins, or `null` if no defaults are configured. */
    var pluginIds: List<String>? = null
        private set

    var version: Long = 0
        private set

    fun apply(event: DefaultPluginsEvent) = apply {
        pluginIds = when (event.payload) {
            is DefaultPluginsSet -> event.payload.pluginIds
            is DefaultPluginsReset -> null
        }

        version = event.version
    }

    fun applyAll(events: List<DefaultPluginsEvent>) = apply {
        events.forEach { apply(it) }
    }
}
