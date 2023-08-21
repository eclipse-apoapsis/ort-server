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

package org.ossreviewtoolkit.server.dao.tables.resolvedconfiguration

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

import org.ossreviewtoolkit.server.model.resolvedconfiguration.PackageCurationProviderConfig

/**
 * A table to represent a [PackageCurationProviderConfig].
 */
object PackageCurationProviderConfigsTable : LongIdTable("package_curation_provider_configs") {
    val name = text("name")
}

class PackageCurationProviderConfigDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PackageCurationProviderConfigDao>(PackageCurationProviderConfigsTable)

    var name by PackageCurationProviderConfigsTable.name

    fun mapToModel() = PackageCurationProviderConfig(name = name)
}
