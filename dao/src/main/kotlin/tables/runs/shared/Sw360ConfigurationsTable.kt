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

package org.ossreviewtoolkit.server.dao.tables.runs.shared

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to represent a SW360 configuration.
 */
object Sw360ConfigurationsTable : LongIdTable("sw360_configurations") {
    val restUrl = text("rest_url")
    val authUrl = text("auth_url")
    val username = text("username")
    val clientId = text("client_id")
}

class Sw360ConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Sw360ConfigurationDao>(Sw360ConfigurationsTable)

    var restUrl by Sw360ConfigurationsTable.restUrl
    var authUrl by Sw360ConfigurationsTable.authUrl
    var username by Sw360ConfigurationsTable.username
    var clientId by Sw360ConfigurationsTable.clientId
}
