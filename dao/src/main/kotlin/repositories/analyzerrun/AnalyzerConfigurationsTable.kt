/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun

import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerConfiguration

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.selectAll

/**
 * A table to represent an analyzer configuration.
 */
object AnalyzerConfigurationsTable : LongIdTable("analyzer_configurations") {
    val analyzerRunId = reference("analyzer_run_id", AnalyzerRunsTable)

    val allowDynamicVersions = bool("allow_dynamic_versions")
    val enabledPackageManagers = text("enabled_package_managers").nullable()
    val disabledPackageManagers = text("disabled_package_managers").nullable()
    val skipExcluded = bool("skip_excluded")

    /**
     * Get the [AnalyzerConfiguration] for the given [analyzerRunId]. Returns `null` if no configuration is found.
     */
    fun getByAnalyzerRunId(analyzerRunId: Long): AnalyzerConfiguration? {
        val resultRow = selectAll().where { AnalyzerConfigurationsTable.analyzerRunId eq analyzerRunId }.singleOrNull()

        if (resultRow == null) return null

        val packageManagers = AnalyzerConfigurationsPackageManagerConfigurationsTable
            .getPackageManagerConfigurationsByAnalyzerConfigurationId(resultRow[id].value)

        return AnalyzerConfiguration(
            allowDynamicVersions = resultRow[allowDynamicVersions],
            enabledPackageManagers =
                resultRow[enabledPackageManagers]?.takeIf { it.isNotEmpty() }?.split(",").orEmpty(),
            disabledPackageManagers = resultRow[disabledPackageManagers]?.takeIf { it.isNotEmpty() }?.split(","),
            packageManagers = packageManagers,
            skipExcluded = resultRow[skipExcluded]
        )
    }
}

class AnalyzerConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AnalyzerConfigurationDao>(AnalyzerConfigurationsTable)

    var analyzerRun by AnalyzerRunDao referencedOn AnalyzerConfigurationsTable.analyzerRunId

    var allowDynamicVersions by AnalyzerConfigurationsTable.allowDynamicVersions
    var enabledPackageManagers: List<String>? by AnalyzerConfigurationsTable.enabledPackageManagers
        .transform({ managers -> managers?.takeIf { it.isNotEmpty() }?.joinToString(",") }, { it?.split(",") })
    var disabledPackageManagers: List<String>? by AnalyzerConfigurationsTable.disabledPackageManagers
        .transform({ managers -> managers?.takeIf { it.isNotEmpty() }?.joinToString(",") }, { it?.split(",") })
    var skipExcluded by AnalyzerConfigurationsTable.skipExcluded

    var packageManagerConfigurations by PackageManagerConfigurationDao via
            AnalyzerConfigurationsPackageManagerConfigurationsTable

    fun mapToModel() = AnalyzerConfiguration(
        allowDynamicVersions = allowDynamicVersions,
        enabledPackageManagers = enabledPackageManagers.orEmpty(),
        disabledPackageManagers = disabledPackageManagers,
        packageManagers = packageManagerConfigurations.associate { it.name to it.mapToModel() },
        skipExcluded = skipExcluded
    )
}
