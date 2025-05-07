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

import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.longLiteral
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert

import org.ossreviewtoolkit.model.utils.DatabaseUtils.transaction

/** A store for [PluginTemplateEvent]s. */
class PluginTemplateEventStore(private val db: Database) {
    private fun loadEvents(name: String, pluginType: PluginType, pluginId: String): List<PluginTemplateEvent> =
        db.transaction {
            PluginTemplateEvents.selectAll()
                .where { PluginTemplateEvents.name eq name }
                .andWhere { PluginTemplateEvents.pluginType eq pluginType }
                .andWhere { PluginTemplateEvents.pluginId eq pluginId }
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

        when (pluginTemplateEvent.payload) {
            is Deleted -> {
                PluginTemplateOrganizationAssignments.deleteWhere {
                    (pluginType eq pluginTemplateEvent.pluginType) and
                            (pluginId eq pluginTemplateEvent.pluginId) and
                            (template_name eq pluginTemplateEvent.name)
                }
            }

            is OrganizationAdded -> {
                PluginTemplateOrganizationAssignments.insert {
                    it[pluginType] = pluginTemplateEvent.pluginType
                    it[pluginId] = pluginTemplateEvent.pluginId
                    it[organizationId] = pluginTemplateEvent.payload.organizationId
                    it[template_name] = pluginTemplateEvent.name
                }
            }

            is OrganizationRemoved -> {
                PluginTemplateOrganizationAssignments.deleteWhere {
                    (pluginType eq pluginTemplateEvent.pluginType) and
                            (pluginId eq pluginTemplateEvent.pluginId) and
                            (organizationId eq pluginTemplateEvent.payload.organizationId) and
                            (template_name eq pluginTemplateEvent.name)
                }
            }

            else -> Unit
        }

        updateReadModel(pluginTemplateEvent)
    }

    /**
     * Get the current state of the plugin with the given [pluginType] and [pluginId]. Returns `null` if no such plugin
     * exists.
     */
    internal fun getPluginTemplate(name: String, pluginType: PluginType, pluginId: String) =
        loadEvents(name, pluginType, pluginId).takeIf { it.isNotEmpty() }?.let {
            PluginTemplateState(name, pluginType, pluginId).applyAll(it)
        }

    private fun updateReadModel(pluginTemplateEvent: PluginTemplateEvent) {
        when (pluginTemplateEvent.payload) {
            is Deleted -> {
                PluginTemplatesReadModel.deleteWhere {
                    PluginTemplatesReadModel.name eq pluginTemplateEvent.name and
                            (PluginTemplatesReadModel.pluginType eq pluginTemplateEvent.pluginType) and
                            (PluginTemplatesReadModel.pluginId eq pluginTemplateEvent.pluginId)
                }
            }

            is GlobalEnabled -> {
                PluginTemplatesReadModel.update(where = {
                    PluginTemplatesReadModel.name eq pluginTemplateEvent.name and
                            (PluginTemplatesReadModel.pluginType eq pluginTemplateEvent.pluginType) and
                            (PluginTemplatesReadModel.pluginId eq pluginTemplateEvent.pluginId)
                }) {
                    it[isGlobal] = true
                }
            }

            is GlobalDisabled -> {
                PluginTemplatesReadModel.update(where = {
                    PluginTemplatesReadModel.name eq pluginTemplateEvent.name and
                            (PluginTemplatesReadModel.pluginType eq pluginTemplateEvent.pluginType) and
                            (PluginTemplatesReadModel.pluginId eq pluginTemplateEvent.pluginId)
                }) {
                    it[isGlobal] = false
                }
            }

            is OptionsUpdated -> {
                PluginTemplatesReadModel.upsert {
                    it[name] = pluginTemplateEvent.name
                    it[pluginType] = pluginTemplateEvent.pluginType
                    it[pluginId] = pluginTemplateEvent.pluginId
                    it[options] = pluginTemplateEvent.payload.options
                }
            }

            is OrganizationAdded -> {
                PluginTemplatesReadModel.update(where = {
                    PluginTemplatesReadModel.name eq pluginTemplateEvent.name and
                            (PluginTemplatesReadModel.pluginType eq pluginTemplateEvent.pluginType) and
                            (PluginTemplatesReadModel.pluginId eq pluginTemplateEvent.pluginId)
                }) {
                    it.update(
                        PluginTemplatesReadModel.organizationIds,
                        CustomFunction(
                            functionName = "array_append",
                            columnType = PluginTemplatesReadModel.organizationIds.columnType,
                            PluginTemplatesReadModel.organizationIds,
                            longLiteral(pluginTemplateEvent.payload.organizationId)
                        )
                    )
                }
            }

            is OrganizationRemoved -> {
                PluginTemplatesReadModel.update(where = {
                    PluginTemplatesReadModel.name eq pluginTemplateEvent.name and
                            (PluginTemplatesReadModel.pluginType eq pluginTemplateEvent.pluginType) and
                            (PluginTemplatesReadModel.pluginId eq pluginTemplateEvent.pluginId)
                }) {
                    it.update(
                        PluginTemplatesReadModel.organizationIds,
                        CustomFunction(
                            functionName = "array_remove",
                            columnType = PluginTemplatesReadModel.organizationIds.columnType,
                            PluginTemplatesReadModel.organizationIds,
                            longLiteral(pluginTemplateEvent.payload.organizationId)
                        )
                    )
                }
            }
        }
    }

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

/** A table to store plugin template events. */
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

/**
 * A table to ensure that only a single template with the same [pluginType] and [pluginId] can exist per organization.
 */
internal object PluginTemplateOrganizationAssignments : Table("plugin_template_organization_assignments") {
    val pluginType = enumerationByName<PluginType>("plugin_type", 255)
    val pluginId = text("plugin_id")
    val organizationId = long("organization_id")
    val template_name = text("template_name")

    override val primaryKey = PrimaryKey(pluginType, pluginId, organizationId)
}

internal object PluginTemplatesReadModel : Table("plugin_templates_read_model") {
    val name = text("name")
    val pluginType = enumerationByName<PluginType>("plugin_type", 255)
    val pluginId = text("plugin_id")
    val options = jsonb<List<PluginOptionTemplate>>("options")
    val isGlobal = bool("is_global")
    val organizationIds = array<Long>("organization_ids")

    override val primaryKey = PrimaryKey(name, pluginType, pluginId)
}
