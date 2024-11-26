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
import org.eclipse.apoapsis.ortserver.model.repositories.OrganizationRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database

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

    override fun list(parameters: ListQueryParameters) =
        db.blockingQuery { OrganizationDao.list(parameters).map { it.mapToModel() } }

    override fun update(id: Long, name: OptionalValue<String>, description: OptionalValue<String?>) = db.blockingQuery {
        val org = OrganizationDao[id]

        name.ifPresent { org.name = it }
        description.ifPresent { org.description = it }

        OrganizationDao[id].mapToModel()
    }

    override fun delete(id: Long) = db.blockingQuery { OrganizationDao[id].delete() }
}
