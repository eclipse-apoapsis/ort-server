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

package org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration

import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackageManagerConfigurationsTable

import org.jetbrains.exposed.sql.Table

/**
 * An intermediate table to store references from
 * [RepositoryAnalyzerConfiguration][RepositoryAnalyzerConfigurationsTable] and
 * [PackageManagerConfiguration][PackageManagerConfigurationsTable].
 */
object RepositoryAnalyzerConfigurationsPackageManagerConfigurationsTable :
    Table("repository_analyzer_configurations_package_manager_configurations") {
    val repositoryAnalyzerConfigurationId =
        reference("repository_analyzer_configuration_id", RepositoryAnalyzerConfigurationsTable)
    val packageManagerConfigurationsId =
        reference("package_manager_configuration_id", PackageManagerConfigurationsTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(
            repositoryAnalyzerConfigurationId,
            packageManagerConfigurationsId,
            name = "${tableName}_pkey"
        )
}
