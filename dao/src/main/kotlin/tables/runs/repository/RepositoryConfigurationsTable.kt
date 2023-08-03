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

import org.ossreviewtoolkit.server.dao.tables.OrtRunDao
import org.ossreviewtoolkit.server.dao.tables.OrtRunsTable

/**
 * A table to represent a repository configuration.
 */
object RepositoryConfigurationsTable : LongIdTable("repository_configurations") {
    val ortRunId = reference("ort_run_id", OrtRunsTable)
    val repositoryAnalyzerConfigurationId =
        reference("repository_analyzer_configuration_id", RepositoryAnalyzerConfigurationsTable).nullable()
}

class RepositoryConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<RepositoryConfigurationDao>(RepositoryConfigurationsTable)

    var ortRun by OrtRunDao referencedOn RepositoryConfigurationsTable.ortRunId

    var repositoryAnalyzerConfiguration by RepositoryAnalyzerConfigurationDao optionalReferencedOn
            RepositoryConfigurationsTable.repositoryAnalyzerConfigurationId
    var issueResolutions by IssueResolutionDao via RepositoryConfigurationsIssueResolutionsTable
    var ruleViolationResolutions by RuleViolationResolutionDao via RepositoryConfigurationsRuleViolationResolutionsTable
    var vulnerabilityResolutions by VulnerabilityResolutionDao via RepositoryConfigurationsVulnerabilityResolutionsTable
    var pathExcludes by PathExcludeDao via RepositoryConfigurationsPathExcludes
    var scopeExcludes by ScopeExcludeDao via RepositoryConfigurationsScopeExcludesTable
    var curations by PackageCurationDao via RepositoryConfigurationsPackageCurationsTable
    var licenseFindingCurations by LicenseFindingCurationDao via RepositoryConfigurationsLicenseFindingCurationsTable
    var packageConfigurations by PackageConfigurationDao via RepositoryConfigurationsPackageConfigurationsTable
    var spdxLicenseChoices by SpdxLicenseChoiceDao via RepositoryConfigurationsSpdxLicenseChoicesTable
    var packageLicenseChoices by PackageLicenseChoiceDao via RepositoryConfigurationsPackageLicenseChoicesTable
}
