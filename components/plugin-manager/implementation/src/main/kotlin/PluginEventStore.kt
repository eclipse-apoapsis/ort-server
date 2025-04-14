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

import org.eclipse.apoapsis.ortserver.dao.utils.jsonb

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.selectAll

import org.ossreviewtoolkit.model.utils.DatabaseUtils.transaction

internal class PluginEventStore(private val db: Database) {
    fun loadEvents(pluginType: PluginType, pluginId: String): List<PluginEvent> = db.transaction {
        PluginEvents.selectAll()
            .where { PluginEvents.pluginType eq pluginType and (PluginEvents.pluginId eq pluginId) }
            .orderBy(PluginEvents.version)
            .map { it.toPluginEvent() }
    }

    fun appendEvent(pluginEvent: PluginEvent): Unit = db.transaction {
        PluginEvents.insert {
            it[pluginType] = pluginEvent.pluginType
            it[pluginId] = pluginEvent.pluginId
            it[version] = pluginEvent.version
            it[payload] = pluginEvent.payload
            it[createdAt] = pluginEvent.createdAt
            it[createdBy] = pluginEvent.createdBy
        }
    }

    private fun ResultRow.toPluginEvent() = PluginEvent(
        pluginType = this[PluginEvents.pluginType],
        pluginId = this[PluginEvents.pluginId],
        version = this[PluginEvents.version],
        payload = this[PluginEvents.payload],
        createdAt = this[PluginEvents.createdAt],
        createdBy = this[PluginEvents.createdBy]
    )
}

/**
 * A table to store plugin events.
 */
internal object PluginEvents : Table("plugin_events") {
    val pluginType = enumerationByName<PluginType>("plugin_type", 255)
    val pluginId = text("plugin_id")
    val version = long("version")
    val payload = jsonb<PluginEventPayload>("payload")
    val createdAt = timestamp("created_at")
    val createdBy = text("created_by")

    override val primaryKey = PrimaryKey(pluginType, pluginId, version)
}
