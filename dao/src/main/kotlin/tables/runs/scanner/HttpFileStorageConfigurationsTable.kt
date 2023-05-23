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

import org.ossreviewtoolkit.server.model.runs.scanner.HttpFileStorageConfiguration

/**
 * A table to represent a configuration of an HTTP file-based storage.
 */
object HttpFileStorageConfigurationsTable : LongIdTable("http_file_storage_configurations") {
    val url = text("url")
    val query = text("query").nullable()
}

class HttpFileStorageConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<HttpFileStorageConfigurationDao>(HttpFileStorageConfigurationsTable) {
        fun findByHttpFileStorageConfiguration(
            httpFileStorageConfiguration: HttpFileStorageConfiguration
        ): HttpFileStorageConfigurationDao? =
            find {
                HttpFileStorageConfigurationsTable.url eq httpFileStorageConfiguration.url and
                        (HttpFileStorageConfigurationsTable.query eq httpFileStorageConfiguration.query)
            }.singleOrNull()

        fun getOrPut(httpFileStorageConfiguration: HttpFileStorageConfiguration): HttpFileStorageConfigurationDao =
            findByHttpFileStorageConfiguration(httpFileStorageConfiguration) ?: new {
                url = httpFileStorageConfiguration.url
                query = httpFileStorageConfiguration.query
            }
    }

    var url by HttpFileStorageConfigurationsTable.url
    var query by HttpFileStorageConfigurationsTable.query

    val headers by HttpFileStorageConfigurationHeaderDao referrersOn
            HttpFileStorageConfigurationHeadersTable.httpFileStorageConfigurationId

    fun mapToModel() = HttpFileStorageConfiguration(
        url = url,
        query = query.orEmpty(),
        headers = headers.associate { it.key to it.value }
    )
}
