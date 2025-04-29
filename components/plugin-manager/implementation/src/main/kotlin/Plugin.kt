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

internal enum class PluginState { ENABLED, DISABLED }

internal class Plugin {
    private var state: PluginState = PluginState.ENABLED

    var version: Long = 0
        private set

    fun apply(event: PluginEvent) = apply {
        state = when (event.payload) {
            is PluginEnabled -> PluginState.ENABLED
            is PluginDisabled -> PluginState.DISABLED
        }
        version = event.version
    }

    fun applyAll(events: List<PluginEvent>) = apply {
        events.forEach { apply(it) }
    }

    fun isEnabled(): Boolean = state == PluginState.ENABLED
}
