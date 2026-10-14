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

import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.dao.utils.enumerationByName
import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.model.OrganizationId

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** A store for [DefaultPluginsEvent]s. */
class DefaultPluginsEventStore(private val db: Database) {
    private suspend fun loadEvents(
        organizationId: OrganizationId?,
        category: DefaultPluginsCategory
    ): List<DefaultPluginsEvent> =
        db.transaction {
            DefaultPluginsEvents.selectAll()
                .where {
                    DefaultPluginsEvents.organizationId.matches(organizationId) and
                            (DefaultPluginsEvents.category eq category)
                }
                .orderBy(DefaultPluginsEvents.version)
                .map { it.toDefaultPluginsEvent() }
        }

    internal suspend fun appendEvent(event: DefaultPluginsEvent): Unit = db.transaction {
        DefaultPluginsEvents.insert {
            it[organizationId] = event.organizationId?.value
            it[category] = event.category
            it[version] = event.version
            it[payload] = event.payload
            it[createdBy] = event.createdBy
            it[createdAt] = event.createdAt
        }

        updateReadModel(event)
    }

    /**
     * Load the state of the default plugins of the given [category] for the given [organizationId], or of the global
     * defaults if [organizationId] is `null`, by applying all events. The state is needed to create the next event. To
     * read the configured default plugins, use [getConfiguredDefaults] instead.
     */
    internal suspend fun loadState(organizationId: OrganizationId?, category: DefaultPluginsCategory) =
        DefaultPluginsState().applyAll(loadEvents(organizationId, category))

    /**
     * Return the default plugins configured for the organization with the given [organizationId] and the global default
     * plugins from the read model. If [organizationId] is `null`, only the global default plugins are returned.
     */
    internal suspend fun getConfiguredDefaults(organizationId: OrganizationId?): ConfiguredDefaultPlugins =
        db.transaction {
            val rows = DefaultPluginsReadModel.selectAll()
                .where {
                    DefaultPluginsReadModel.organizationId.isNull() or
                            DefaultPluginsReadModel.organizationId.matches(organizationId)
                }
                .toList()

            fun pluginIdsByCategory(global: Boolean) =
                rows.filter { (it[DefaultPluginsReadModel.organizationId] == null) == global }
                    .associate { it[DefaultPluginsReadModel.category] to it[DefaultPluginsReadModel.pluginIds] }

            ConfiguredDefaultPlugins(
                organization = if (organizationId == null) emptyMap() else pluginIdsByCategory(global = false),
                global = pluginIdsByCategory(global = true)
            )
        }

    private fun updateReadModel(event: DefaultPluginsEvent) {
        DefaultPluginsReadModel.deleteWhere {
            DefaultPluginsReadModel.organizationId.matches(event.organizationId) and
                    (DefaultPluginsReadModel.category eq event.category)
        }

        when (event.payload) {
            is DefaultPluginsSet -> {
                DefaultPluginsReadModel.insert {
                    it[organizationId] = event.organizationId?.value
                    it[category] = event.category
                    it[pluginIds] = event.payload.pluginIds
                }
            }

            is DefaultPluginsReset -> Unit
        }
    }

    private fun ResultRow.toDefaultPluginsEvent() = DefaultPluginsEvent(
        organizationId = this[DefaultPluginsEvents.organizationId]?.let { OrganizationId(it) },
        category = this[DefaultPluginsEvents.category],
        version = this[DefaultPluginsEvents.version],
        payload = this[DefaultPluginsEvents.payload],
        createdBy = this[DefaultPluginsEvents.createdBy],
        createdAt = this[DefaultPluginsEvents.createdAt]
    )
}

/**
 * The default plugins configured for an [organization] and [globally][global], as ordered plugin IDs by category.
 * Categories without configured default plugins have no entry.
 */
internal class ConfiguredDefaultPlugins(
    val organization: Map<DefaultPluginsCategory, List<String>>,
    val global: Map<DefaultPluginsCategory, List<String>>
)

/** Return a condition matching this column against [organizationId], where `null` matches the global scope. */
internal fun Column<Long?>.matches(organizationId: OrganizationId?): Op<Boolean> =
    if (organizationId == null) isNull() else eq(organizationId.value)

/**
 * A table to store default plugins events. A `null` [organizationId] denotes events for the global defaults.
 */
internal object DefaultPluginsEvents : Table("default_plugins_events") {
    val organizationId = long("organization_id").nullable()
    val category = enumerationByName<DefaultPluginsCategory>("category")
    val version = long("version")
    val payload = jsonb<DefaultPluginsEventPayload>("payload")
    val createdBy = text("created_by")
    val createdAt = timestamp("created_at")
}

/**
 * A read model containing the currently configured default plugins. A `null` [organizationId] denotes the global
 * defaults. If no row exists for a scope and category, no defaults are configured for it.
 */
internal object DefaultPluginsReadModel : Table("default_plugins_read_model") {
    val organizationId = long("organization_id").nullable()
    val category = enumerationByName<DefaultPluginsCategory>("category")
    val pluginIds = array<String>("plugin_ids")
}
