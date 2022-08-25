/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.repositories

import org.jetbrains.exposed.exceptions.ExposedSQLException

import org.ossreviewtoolkit.server.dao.PostgresErrorCodes
import org.ossreviewtoolkit.server.dao.UniqueConstraintException
import org.ossreviewtoolkit.server.dao.dbQuery
import org.ossreviewtoolkit.server.dao.entities.OrganizationDao
import org.ossreviewtoolkit.server.dao.entities.OrganizationsTable
import org.ossreviewtoolkit.server.shared.models.api.Organization

object OrganizationsRepository {

    /**
     * Insert an [Organization] into the [OrganizationsTable] and return the resulting [OrganizationEntity].
     */
    suspend fun createOrganization(name: String, description: String) = dbQuery {
        OrganizationDao.new {
            this.name = name
            this.description = description
        }
    }.onFailure {
        if (it is ExposedSQLException) {
            when (it.sqlState) {
                PostgresErrorCodes.UNIQUE_CONSTRAINT_VIOLATION.value -> {
                    throw UniqueConstraintException(
                        "Failed to create organization '$name', as an organization with this name already exists.",
                        it
                    )
                }
            }
        }

        throw it
    }.getOrThrow().mapToEntity()

    /**
     * Get a single [Organization] by its [id]. Returns null if no Organization was found.
     */
    suspend fun getOrganization(id: Long) = dbQuery {
        OrganizationDao[id].mapToEntity()
    }.getOrNull()

    /**
     * List all organizations.
     */
    suspend fun listOrganizations() = dbQuery {
        OrganizationDao.all().map { it.mapToEntity() }
    }.getOrThrow()

    /**
     * Update the organization with [id] overwriting the [name] and / or the [description].
     */
    suspend fun updateOrganization(id: Long, name: String? = null, description: String? = null) = dbQuery {
        val org = OrganizationDao[id]
        name?.let { org.name = it }
        description?.let { org.description = it }

        OrganizationDao[id].mapToEntity()
    }.getOrThrow()
}
