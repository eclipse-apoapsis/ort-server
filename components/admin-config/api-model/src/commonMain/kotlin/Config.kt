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

package org.eclipse.apoapsis.ortserver.components.adminconfig

import kotlinx.serialization.Serializable

/**
 * A configuration entry.
 */
@Serializable
data class Config(
    /** The value of the configuration entry. If null, the [default value][ConfigKey.default] is used. */
    val value: String?,

    /** Whether the configuration entry is enabled. If `false`, the [default value][ConfigKey.default] is used. */
    val isEnabled: Boolean
)

/**
 * The supported configuration keys. The keys are specified as enumerable values to prevent addition of arbitrary keys
 * to the table. New keys should be added by adding a new entry to this enum class, possibly with a default value.
 */
enum class ConfigKey(val default: String) {
    HOME_ICON_URL("https://example.com/icon.png"),
    MAIN_PRODUCT_NAME("ORT Server"),
}
