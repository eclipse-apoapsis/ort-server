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

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert

/**
 * A configuration entry.
 *
 * @property value The value of the configuration entry. If null, the default value is used.
 * @property isEnabled Whether the configuration entry is enabled or not.
 */
@Serializable
data class Config(
    val value: String?,
    val isEnabled: Boolean
)

/**
 * The supported configuration keys. The keys are specified as enumerable values
 * to prevent addition of arbitrary keys to the table. New keys should be added
 * by adding a new entry to this enum class, possibly with a default value.
 */
enum class ConfigKey(val default: String) {
    HOME_ICON_URL("https://example.com/icon.png"),
    MAIN_PRODUCT_NAME("ORT Server"),
}

object ConfigTable : Table("config_table") {
    val key = enumerationByName<ConfigKey>("key", 255)
    val isEnabled = bool("is_enabled").default(false)
    val value = text("value")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(key, name = "PK_ConfigTable_key")

    // If the config key doesn't exist in the database, return the default value of the enum.
    // Also return the isEnabled status as false, to handle gracefully the case of missing config
    // key in the UI, by falling back to using a default value.
    fun get(key: ConfigKey): Config =
        select(value, isEnabled)
            .where { ConfigTable.key eq key }
            .map { Config(value = it[value], it[isEnabled]) }
            .firstOrNull() ?: Config(key.default, false)

    fun insertOrUpdate(key: ConfigKey, value: String?, isEnabled: Boolean) {
        upsert {
            it[ConfigTable.key] = key
            it[ConfigTable.value] = value ?: key.default
            it[ConfigTable.isEnabled] = isEnabled
            it[updatedAt] = Clock.System.now()
        }
    }
}
