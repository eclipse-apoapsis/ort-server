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

import org.eclipse.apoapsis.ortserver.model.runs.scanner.PostgresConnection

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent a configuration of a PostgreSQL connection.
 */
object PostgresConnectionsTable : LongIdTable("postgres_connections") {
    val url = text("url")
    val schema = text("schema")
    val username = text("username")
    val sslMode = text("ssl_mode")
    val sslCert = text("ssl_cert").nullable()
    val sslKey = text("ssl_key").nullable()
    val sslRootCert = text("ssl_root_cert").nullable()
    val parallelTransactions = integer("parallel_transactions")
}

class PostgresConnectionDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PostgresConnectionDao>(PostgresConnectionsTable) {
        fun findByPostgresConnection(postgresConnection: PostgresConnection): PostgresConnectionDao? =
            find {
                PostgresConnectionsTable.url eq postgresConnection.url and
                        (PostgresConnectionsTable.schema eq postgresConnection.schema) and
                        (PostgresConnectionsTable.username eq postgresConnection.username) and
                        (PostgresConnectionsTable.sslMode eq postgresConnection.sslMode) and
                        (PostgresConnectionsTable.sslCert eq postgresConnection.sslCert) and
                        (PostgresConnectionsTable.sslKey eq postgresConnection.sslKey) and
                        (PostgresConnectionsTable.sslRootCert eq postgresConnection.sslRootCert) and
                        (PostgresConnectionsTable.parallelTransactions eq postgresConnection.parallelTransactions)
            }.singleOrNull()

        fun getOrPut(postgresConnection: PostgresConnection): PostgresConnectionDao =
            findByPostgresConnection(postgresConnection) ?: new {
                url = postgresConnection.url
                schema = postgresConnection.schema
                username = postgresConnection.username
                sslMode = postgresConnection.sslMode
                sslCert = postgresConnection.sslCert
                sslKey = postgresConnection.sslKey
                sslRootCert = postgresConnection.sslRootCert
                parallelTransactions = postgresConnection.parallelTransactions
            }
    }

    var url by PostgresConnectionsTable.url
    var schema by PostgresConnectionsTable.schema
    var username by PostgresConnectionsTable.username
    var sslMode by PostgresConnectionsTable.sslMode
    var sslCert by PostgresConnectionsTable.sslCert
    var sslKey by PostgresConnectionsTable.sslKey
    var sslRootCert by PostgresConnectionsTable.sslRootCert
    var parallelTransactions by PostgresConnectionsTable.parallelTransactions

    fun mapToModel() = PostgresConnection(
        url = url,
        schema = schema,
        username = username,
        sslMode = sslMode,
        sslCert = sslCert,
        sslKey = sslKey,
        sslRootCert = sslRootCert,
        parallelTransactions = parallelTransactions
    )
}
