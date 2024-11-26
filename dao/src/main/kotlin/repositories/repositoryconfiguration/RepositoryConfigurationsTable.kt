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

import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.model.runs.repository.Curations
import org.eclipse.apoapsis.ortserver.model.runs.repository.Excludes
import org.eclipse.apoapsis.ortserver.model.runs.repository.LicenseChoices
import org.eclipse.apoapsis.ortserver.model.runs.repository.RepositoryConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.repository.Resolutions

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

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
    var provenanceSnippetChoices by SnippetChoicesDao via RepositoryConfigurationsProvenanceSnippetChoicesTable

    fun mapToModel() = RepositoryConfiguration(
        id = id.value,
        ortRunId = ortRun.id.value,
        analyzerConfig = repositoryAnalyzerConfiguration?.mapToModel(),
        excludes = Excludes(
            paths = pathExcludes.map(PathExcludeDao::mapToModel),
            scopes = scopeExcludes.map(ScopeExcludeDao::mapToModel)
        ),
        resolutions = Resolutions(
            issues = issueResolutions.map(IssueResolutionDao::mapToModel),
            ruleViolations = ruleViolationResolutions.map(RuleViolationResolutionDao::mapToModel),
            vulnerabilities = vulnerabilityResolutions.map(VulnerabilityResolutionDao::mapToModel)
        ),
        curations = Curations(
            packages = curations.map(PackageCurationDao::mapToModel),
            licenseFindings = licenseFindingCurations.map(LicenseFindingCurationDao::mapToModel)
        ),
        packageConfigurations = packageConfigurations.map(PackageConfigurationDao::mapToModel),
        licenseChoices = LicenseChoices(
            repositoryLicenseChoices = spdxLicenseChoices.map(SpdxLicenseChoiceDao::mapToModel),
            packageLicenseChoices = packageLicenseChoices.map(PackageLicenseChoiceDao::mapToModel)
        ),
        provenanceSnippetChoices = provenanceSnippetChoices.map(SnippetChoicesDao::mapToModel)
    )
}
