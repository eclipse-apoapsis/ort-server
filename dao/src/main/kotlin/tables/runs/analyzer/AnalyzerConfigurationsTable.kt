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

import org.ossreviewtoolkit.server.dao.tables.runs.shared.Sw360ConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.Sw360ConfigurationsTable
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration

/**
 * A table to represent an analyzer configuration.
 */
object AnalyzerConfigurationsTable : LongIdTable("analyzer_configurations") {
    val sw360Configuration = reference("sw360_configuration_id", Sw360ConfigurationsTable.id).nullable()
    val allowDynamicVersions = bool("allow_dynamic_versions")
}

class AnalyzerConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AnalyzerConfigurationDao>(AnalyzerConfigurationsTable)

    var allowDynamicVersions by AnalyzerConfigurationsTable.allowDynamicVersions
    var sw360Configuration by Sw360ConfigurationDao optionalReferencedOn AnalyzerConfigurationsTable.sw360Configuration
    val enabledPackageManagers by EnabledPackageManagerDao referrersOn EnabledPackageManagersTable.analyzerConfiguration
    val disabledPackageManagers by DisabledPackageManagerDao referrersOn
            DisabledPackageManagersTable.analyzerConfiguration
    val packageManagerConfiguration by PackageManagerConfigurationDao referrersOn
            PackageManagerConfigurationsTable.analyzerConfiguration

    fun mapToModel() = AnalyzerConfiguration(
        id.value,
        allowDynamicVersions,
        enabledPackageManagers.map(EnabledPackageManagerDao::packageManager),
        disabledPackageManagers.map(DisabledPackageManagerDao::packageManager),
        packageManagerConfiguration.associate { it.name to it.mapToModel() },
        sw360Configuration?.mapToModel()
    )
}
