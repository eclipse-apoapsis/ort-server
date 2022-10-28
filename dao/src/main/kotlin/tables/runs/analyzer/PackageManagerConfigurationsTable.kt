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

package org.ossreviewtoolkit.server.dao.tables.runs.analyzer

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

import org.ossreviewtoolkit.server.dao.tables.runs.shared.OptionDao
import org.ossreviewtoolkit.server.model.runs.PackageManagerConfiguration

/**
 * A table to represent a package manager configuration.
 */
object PackageManagerConfigurationsTable : LongIdTable("package_manager_configurations") {
    val analyzerConfiguration = reference(
        "analyzer_configuration_id",
        AnalyzerConfigurationsTable.id,
        ReferenceOption.CASCADE
    )
    val name = text("name")
}

class PackageManagerConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PackageManagerConfigurationDao>(PackageManagerConfigurationsTable)

    var name by PackageManagerConfigurationsTable.name
    var analyzerConfiguration by AnalyzerConfigurationDao referencedOn
            PackageManagerConfigurationsTable.analyzerConfiguration
    val mustRunAfter by PackageManagerConfigurationMustRunAfterDao referrersOn
            PackageManagerConfigurationsMustRunAfterTable.packageManagerConfiguration
    var options by OptionDao via PackageManagerConfigurationsOptionsTable

    fun mapToModel() = PackageManagerConfiguration(
        id.value,
        mustRunAfter.map(PackageManagerConfigurationMustRunAfterDao::name),
        options.associate { it.name to it.value }
    )
}
