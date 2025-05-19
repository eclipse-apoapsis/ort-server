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

package org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun

import org.eclipse.apoapsis.ortserver.model.runs.PackageManagerConfiguration

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll

/**
 * A junction table to store references from [AnalyzerConfigurationsTable] and [PackageManagerConfigurationsTable].
 */
object AnalyzerConfigurationsPackageManagerConfigurationsTable :
    Table("analyzer_configurations_package_manager_configurations") {
    val analyzerConfigurationId = reference("analyzer_configuration_id", AnalyzerConfigurationsTable)
    val packageManagerConfigurationId = reference("package_manager_configuration_id", PackageManagerConfigurationsTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(analyzerConfigurationId, packageManagerConfigurationId, name = "${tableName}_pkey")

    /**
     * Get the map of package manager IDs associated with their [PackageManagerConfiguration] for the given
     * [analyzerConfigurationId]. Returns `null` if no configurations are found.
     */
    fun getPackageManagerConfigurationsByAnalyzerConfigurationId(
        analyzerConfigurationId: Long
    ): Map<String, PackageManagerConfiguration>? {
        val resultRows = innerJoin(PackageManagerConfigurationsTable)
            .leftJoin(PackageManagerConfigurationOptionsTable)
            .selectAll()
            .where {
                AnalyzerConfigurationsPackageManagerConfigurationsTable.analyzerConfigurationId eq
                        analyzerConfigurationId
            }
            .toList()

        if (resultRows.isEmpty()) return null

        return resultRows.groupBy { it[PackageManagerConfigurationsTable.name] }.mapValues { (_, rows) ->
            @Suppress("UNCHECKED_CAST")
            val options = if (rows[0][PackageManagerConfigurationsTable.hasOptions]) {
                // The compiler wrongly assumes that the option keys cannot be null, but they can be if there are no
                // entries in the joined tables, so they must be cast to nullable.
                val nullableOptions = rows.associate {
                    it[PackageManagerConfigurationOptionsTable.name] to
                            it[PackageManagerConfigurationOptionsTable.value]
                } as Map<String?, String>

                nullableOptions.filterKeys { it != null } as Map<String, String>
            } else {
                null
            }

            PackageManagerConfiguration(
                mustRunAfter = rows[0][PackageManagerConfigurationsTable.mustRunAfter]?.split(','),
                options = options
            )
        }
    }
}
