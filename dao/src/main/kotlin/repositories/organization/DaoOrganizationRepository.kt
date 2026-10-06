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

import org.eclipse.apoapsis.ortserver.dao.getEntityOrNull
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.dao.transactionCatching
import org.eclipse.apoapsis.ortserver.dao.utils.apply
import org.eclipse.apoapsis.ortserver.dao.utils.applyIRegex
import org.eclipse.apoapsis.ortserver.dao.utils.extractIds
import org.eclipse.apoapsis.ortserver.dao.utils.listQuery
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyLevel
import org.eclipse.apoapsis.ortserver.model.repositories.OrganizationRepository
import org.eclipse.apoapsis.ortserver.model.util.FilterParameter
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * An implementation of [OrganizationRepository] that stores organizations in [OrganizationsTable].
 */
class DaoOrganizationRepository(private val db: Database) : OrganizationRepository {
    override suspend fun create(name: String, description: String?) = db.transaction {
        OrganizationDao.new {
            this.name = name
            this.description = description
        }
    }.mapToModel()

    override suspend fun get(id: Long) = db.transactionCatching { OrganizationDao[id].mapToModel() }.getEntityOrNull()

    override suspend fun list(
        parameters: ListQueryParameters,
        nameFilter: FilterParameter?,
        hierarchyFilter: HierarchyFilter
    ) =
        db.transaction {
            val nameCondition = nameFilter?.let {
                OrganizationsTable.name.applyIRegex(it.value)
            } ?: Op.TRUE

            val builder = hierarchyFilter.apply(nameCondition) { level, ids, filter ->
                generateHierarchyCondition(level, ids, filter)
            }

            OrganizationDao.listQuery(parameters, OrganizationDao::mapToModel, builder)
        }

    override suspend fun update(id: Long, name: OptionalValue<String>, description: OptionalValue<String?>) =
        db.transaction {
            val org = OrganizationDao[id]

            name.ifPresent { org.name = it }
            description.ifPresent { org.description = it }

            OrganizationDao[id].mapToModel()
        }

    override suspend fun delete(id: Long) = db.transaction { OrganizationDao[id].delete() }
}

/**
 * Generate a condition defined by a [filter] for the given [level] and [ids].
 */
private fun generateHierarchyCondition(
    level: HierarchyLevel,
    ids: List<CompoundHierarchyId>,
    filter: HierarchyFilter
): Op<Boolean> =
    when (level) {
        HierarchyLevel.ORGANIZATION ->
            OrganizationsTable.id inList (
                ids.extractIds(HierarchyLevel.ORGANIZATION) +
                    filter.nonTransitiveIncludes[HierarchyLevel.ORGANIZATION].orEmpty()
                        .extractIds(HierarchyLevel.ORGANIZATION)
            )

        else -> Op.FALSE
    }
