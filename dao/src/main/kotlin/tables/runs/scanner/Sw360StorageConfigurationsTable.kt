/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.tables.runs.scanner

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

import org.ossreviewtoolkit.server.model.runs.scanner.Sw360StorageConfiguration

/**
 * A table to represent a configuration of a SW360 storage.
 */
object Sw360StorageConfigurationsTable : LongIdTable("sw360_storage_configurations") {
    val restUrl = text("rest_url")
    val authUrl = text("auth_url")
    val username = text("username")
    val clientId = text("client_id")
}

class Sw360StorageConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Sw360StorageConfigurationDao>(Sw360StorageConfigurationsTable) {
        fun findBySw360StorageConfiguration(
            sw360StorageConfiguration: Sw360StorageConfiguration
        ): Sw360StorageConfigurationDao? =
            find {
                Sw360StorageConfigurationsTable.restUrl eq sw360StorageConfiguration.restUrl and
                        (Sw360StorageConfigurationsTable.authUrl eq sw360StorageConfiguration.authUrl) and
                        (Sw360StorageConfigurationsTable.username eq sw360StorageConfiguration.username) and
                        (Sw360StorageConfigurationsTable.clientId eq sw360StorageConfiguration.clientId)
            }.singleOrNull()

        fun getOrPut(sw360StorageConfiguration: Sw360StorageConfiguration): Sw360StorageConfigurationDao =
            findBySw360StorageConfiguration(sw360StorageConfiguration) ?: new {
                restUrl = sw360StorageConfiguration.restUrl
                authUrl = sw360StorageConfiguration.authUrl
                username = sw360StorageConfiguration.username
                clientId = sw360StorageConfiguration.clientId
            }
    }

    var restUrl by Sw360StorageConfigurationsTable.restUrl
    var authUrl by Sw360StorageConfigurationsTable.authUrl
    var username by Sw360StorageConfigurationsTable.username
    var clientId by Sw360StorageConfigurationsTable.clientId

    fun mapToModel() = Sw360StorageConfiguration(
        restUrl = restUrl,
        authUrl = authUrl,
        username = username,
        clientId = clientId
    )
}
