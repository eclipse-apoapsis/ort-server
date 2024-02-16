/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.tables.runs.scanner

import org.eclipse.apoapsis.ortserver.model.runs.scanner.PostgresStorageConfiguration

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to represent a configuration of a PostgreSQL storage configuration.
 */
object PostgresStorageConfigurationsTable : LongIdTable("postgres_storage_configurations") {
    val postgresConnectionId = reference("postgres_connection_id", PostgresConnectionsTable)

    val type = text("type")
}

class PostgresStorageConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PostgresStorageConfigurationDao>(PostgresStorageConfigurationsTable)

    var postgresConnection by PostgresConnectionDao referencedOn PostgresStorageConfigurationsTable.postgresConnectionId

    var type by PostgresStorageConfigurationsTable.type

    fun mapToModel() = PostgresStorageConfiguration(
        connection = postgresConnection.mapToModel(),
        type = type
    )
}
