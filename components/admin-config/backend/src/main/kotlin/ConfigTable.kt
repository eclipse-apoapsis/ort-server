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

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.upsert

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
