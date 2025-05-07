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

class PluginTemplateEventStore(private val db: Database) {
    private fun loadEvents(pluginType: PluginType, pluginId: String): List<PluginTemplateEvent> = db.transaction {
        PluginTemplateEvents.selectAll()
            .where { PluginTemplateEvents.pluginType eq pluginType and (PluginTemplateEvents.pluginId eq pluginId) }
            .orderBy(PluginTemplateEvents.version)
            .map { it.toPluginTemplateEvent() }
    }

    internal fun appendEvent(pluginTemplateEvent: PluginTemplateEvent): Unit = db.transaction {
        PluginTemplateEvents.insert {
            it[name] = pluginTemplateEvent.name
            it[pluginType] = pluginTemplateEvent.pluginType
            it[pluginId] = pluginTemplateEvent.pluginId
            it[version] = pluginTemplateEvent.version
            it[payload] = pluginTemplateEvent.payload
            it[createdBy] = pluginTemplateEvent.createdBy
            it[createdAt] = pluginTemplateEvent.createdAt
        }
    }

    /**
     * Get the current state of the plugin with the given [pluginType] and [pluginId].
     */
    internal fun getPluginTemplate(name: String, pluginType: PluginType, pluginId: String) =
        PluginTemplateState().applyAll(loadEvents(pluginType, pluginId))

    private fun ResultRow.toPluginTemplateEvent() = PluginTemplateEvent(
        name = this[PluginTemplateEvents.name],
        pluginType = this[PluginTemplateEvents.pluginType],
        pluginId = this[PluginTemplateEvents.pluginId],
        version = this[PluginTemplateEvents.version],
        payload = this[PluginTemplateEvents.payload],
        createdBy = this[PluginTemplateEvents.createdBy],
        createdAt = this[PluginTemplateEvents.createdAt]
    )
}

internal object PluginTemplateEvents : Table("plugin_template_events") {
    val name = text("name")
    val pluginType = enumerationByName<PluginType>("plugin_type", 255)
    val pluginId = text("plugin_id")
    val version = long("version")
    val payload = jsonb<PluginTemplateEventPayload>("payload")
    val createdBy = text("created_by")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(name, pluginType, pluginId, version)
}
