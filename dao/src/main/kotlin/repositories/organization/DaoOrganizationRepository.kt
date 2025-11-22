/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.organization

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.utils.apply
import org.eclipse.apoapsis.ortserver.dao.utils.applyIRegex
import org.eclipse.apoapsis.ortserver.dao.utils.extractIds
import org.eclipse.apoapsis.ortserver.dao.utils.listQuery
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.repositories.OrganizationRepository
import org.eclipse.apoapsis.ortserver.model.util.FilterParameter
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder

/**
 * An implementation of [OrganizationRepository] that stores organizations in [OrganizationsTable].
 */
class DaoOrganizationRepository(private val db: Database) : OrganizationRepository {
    override fun create(name: String, description: String?) = db.blockingQuery {
        OrganizationDao.new {
            this.name = name
            this.description = description
        }
    }.mapToModel()

    override fun get(id: Long) = db.entityQuery { OrganizationDao[id].mapToModel() }

    override fun list(parameters: ListQueryParameters, nameFilter: FilterParameter?, hierarchyFilter: HierarchyFilter) =
        db.blockingQuery {
            val nameCondition = nameFilter?.let {
                OrganizationsTable.name.applyIRegex(it.value)
            } ?: Op.TRUE

            val builder = hierarchyFilter.apply(nameCondition) { level, ids, filter ->
                generateHierarchyCondition(level, ids, filter)
            }

            OrganizationDao.listQuery(parameters, OrganizationDao::mapToModel, builder)
        }

    override fun update(id: Long, name: OptionalValue<String>, description: OptionalValue<String?>) = db.blockingQuery {
        val org = OrganizationDao[id]

        name.ifPresent { org.name = it }
        description.ifPresent { org.description = it }

        OrganizationDao[id].mapToModel()
    }

    override fun delete(id: Long) = db.blockingQuery { OrganizationDao[id].delete() }
}

/**
 * Generate a condition defined by a [filter] for the given [level] and [ids].
 */
private fun SqlExpressionBuilder.generateHierarchyCondition(
    level: Int,
    ids: List<CompoundHierarchyId>,
    filter: HierarchyFilter
): Op<Boolean> =
    when (level) {
        CompoundHierarchyId.ORGANIZATION_LEVEL ->
            OrganizationsTable.id inList (
                ids.extractIds(CompoundHierarchyId.ORGANIZATION_LEVEL) +
                    filter.nonTransitiveIncludes[CompoundHierarchyId.ORGANIZATION_LEVEL].orEmpty()
                        .extractIds(CompoundHierarchyId.ORGANIZATION_LEVEL)
            )
        else -> Op.FALSE
    }
