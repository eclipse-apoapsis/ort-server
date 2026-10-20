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

package org.eclipse.apoapsis.ortserver.components.pluginmanager.queries

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplatesReadModel
import org.eclipse.apoapsis.ortserver.dao.Query
import org.eclipse.apoapsis.ortserver.model.OrganizationId

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * A query to get the plugin templates that apply to the organization with the given [organizationId]. For each
 * plugin, this is the template that is assigned to the organization, or the global template if no template is assigned
 * to the organization. This matches the result of [GetPluginTemplateForOrganizationQuery] for all plugins at once.
 */
internal class GetPluginTemplatesForOrganizationQuery(val organizationId: OrganizationId) :
    Query<List<PluginTemplate>> {
    override fun execute(): List<PluginTemplate> =
        PluginTemplatesReadModel
            .selectAll()
            .where {
                (organizationId.value inArray PluginTemplatesReadModel.organizationIds) or
                        (PluginTemplatesReadModel.isGlobal eq true)
            }
            .map { it.toPluginTemplate() }
            .groupBy { it.pluginType to it.pluginId }
            .map { (_, templates) ->
                templates.find { organizationId.value in it.organizationIds } ?: templates.first()
            }
}
