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

package org.eclipse.apoapsis.ortserver.components.pluginmanager.queries

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplatesReadModel
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.dao.Query

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.longLiteral
import org.jetbrains.exposed.sql.selectAll

internal class GetPluginTemplateForOrganizationQuery(
    val pluginType: PluginType,
    val pluginId: String,
    val organizationId: Long
) : Query<PluginTemplate?> {
    override fun execute(): PluginTemplate? {
        val template = PluginTemplatesReadModel
            .selectAll()
            .where { PluginTemplatesReadModel.pluginType eq pluginType }
            .andWhere { PluginTemplatesReadModel.pluginId eq pluginId }
            .andWhere { organizationId inArray PluginTemplatesReadModel.organizationIds }
            .singleOrNull()
            ?: PluginTemplatesReadModel
                .selectAll()
                .where { PluginTemplatesReadModel.pluginType eq pluginType }
                .andWhere { PluginTemplatesReadModel.pluginId eq pluginId }
                .andWhere { PluginTemplatesReadModel.isGlobal eq true }
                .singleOrNull()

        return template?.let {
            PluginTemplate(
                name = it[PluginTemplatesReadModel.name],
                pluginType = it[PluginTemplatesReadModel.pluginType],
                pluginId = it[PluginTemplatesReadModel.pluginId],
                options = it[PluginTemplatesReadModel.options],
                isGlobal = it[PluginTemplatesReadModel.isGlobal],
                organizationIds = it[PluginTemplatesReadModel.organizationIds]
            )
        }
    }
}

private infix fun Long.inArray(arrayColumn: Expression<List<Long>>): Op<Boolean> =
    object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            queryBuilder.append("(")
            queryBuilder.append(longLiteral(this@inArray))
            queryBuilder.append(" = ANY(")
            queryBuilder.append(arrayColumn)
            queryBuilder.append("))")
        }
    }
