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

package org.ossreviewtoolkit.server.dao.tables.runs.repository

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.PackageManagerConfigurationDao

/**
 * A table to store a repository analyzer configuration, used within a
 * [RepositoryConfiguration][RepositoryConfigurationsTable].
 */
object RepositoryAnalyzerConfigurationsTable : LongIdTable("repository_analyzer_configurations") {
    val allowDynamicVersions = bool("allow_dynamic_versions").nullable()
    val enabledPackageManagers = text("enabled_package_managers").nullable()
    val disabledPackageManagers = text("disabled_package_managers").nullable()
    val skipExcluded = bool("skip_excluded").nullable()
}

class RepositoryAnalyzerConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<RepositoryAnalyzerConfigurationDao>(RepositoryAnalyzerConfigurationsTable)

    var allowDynamicVersions by RepositoryAnalyzerConfigurationsTable.allowDynamicVersions
    var enabledPackageManagers: List<String>? by RepositoryAnalyzerConfigurationsTable.enabledPackageManagers
        .transform({ it?.joinToString(",") }, { it?.split(",") })
    var disabledPackageManagers: List<String>? by RepositoryAnalyzerConfigurationsTable.disabledPackageManagers
        .transform({ it?.joinToString(",") }, { it?.split(",") })
    var skipExcluded by RepositoryAnalyzerConfigurationsTable.skipExcluded
    var packageManagerConfigurations by PackageManagerConfigurationDao via
            RepositoryAnalyzerConfigurationsPackageManagerConfigurationsTable
}
