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

package org.ossreviewtoolkit.server.dao.tables

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType

/**
 * A table to represent a repository inside a product.
 */
object RepositoriesTable : LongIdTable("repositories") {
    val type = enumerationByName<RepositoryType>("type", 128)
    val url = text("url")
    val productId = reference("product_id", ProductsTable.id)
}

class RepositoryDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<RepositoryDao>(RepositoriesTable)

    var type by RepositoriesTable.type
    var url by RepositoriesTable.url
    var product by ProductDao referencedOn RepositoriesTable.productId

    fun mapToModel() = Repository(id = id.value, type = type, url = url)
}
