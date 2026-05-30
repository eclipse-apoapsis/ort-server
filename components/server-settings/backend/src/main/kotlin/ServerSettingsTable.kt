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

package org.eclipse.apoapsis.ortserver.components.serversettings

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.dao.utils.enumerationByName

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

object ServerSettingsTable : Table("server_settings") {
    val key = enumerationByName<ServerSettingKey>("key")
    val isEnabled = bool("is_enabled").default(false)
    val value = text("value")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(key, name = "PK_ServerSettings_key")

    /**
     * Get the [ServerSetting] for the provided [key]. If no value for the [key] is stored in the database, the
     * [default value][ServerSettingKey.default] is returned and [ServerSetting.isEnabled] default to `false` in this
     * case.
     */
    fun get(key: ServerSettingKey): ServerSetting =
        select(value, isEnabled)
            .where { ServerSettingsTable.key eq key }
            .map { ServerSetting(value = it[value], it[isEnabled]) }
            .firstOrNull() ?: ServerSetting(key.default, false)

    /**
     * Update [value] and [isEnabled] for the provided [key]. If no entry for the [key] is stored in the database, a new
     * entry is created.
     */
    fun insertOrUpdate(key: ServerSettingKey, value: String?, isEnabled: Boolean) {
        upsert {
            it[ServerSettingsTable.key] = key
            it[ServerSettingsTable.value] = value ?: key.default
            it[ServerSettingsTable.isEnabled] = isEnabled
            it[ServerSettingsTable.updatedAt] = Clock.System.now()
        }
    }
}
