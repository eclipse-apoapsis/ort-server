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

package org.ossreviewtoolkit.server.dao.repositories

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SizedCollection

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.entityQuery
import org.ossreviewtoolkit.server.dao.tables.OrtRunDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.IssueResolutionDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.LicenseFindingCurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.PackageConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.PackageCurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.PackageCurationDataDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.PackageLicenseChoiceDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.PathExcludeDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.RepositoryAnalyzerConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.RepositoryConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.RepositoryConfigurationsTable
import org.ossreviewtoolkit.server.dao.tables.runs.repository.RuleViolationResolutionDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.ScopeExcludeDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.SpdxLicenseChoiceDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.VulnerabilityResolutionDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifierDao
import org.ossreviewtoolkit.server.model.repositories.RepositoryConfigurationRepository
import org.ossreviewtoolkit.server.model.runs.repository.Curations
import org.ossreviewtoolkit.server.model.runs.repository.Excludes
import org.ossreviewtoolkit.server.model.runs.repository.LicenseChoices
import org.ossreviewtoolkit.server.model.runs.repository.PackageConfiguration
import org.ossreviewtoolkit.server.model.runs.repository.PackageCuration
import org.ossreviewtoolkit.server.model.runs.repository.PackageLicenseChoice
import org.ossreviewtoolkit.server.model.runs.repository.RepositoryAnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.repository.RepositoryConfiguration
import org.ossreviewtoolkit.server.model.runs.repository.Resolutions

/**
 * An implementation of [RepositoryConfigurationRepository] that stores repository configurations in
 * [RepositoryConfigurationsTable].
 */
class DaoRepositoryConfigurationRepository(private val db: Database) : RepositoryConfigurationRepository {
    override fun create(
        ortRunId: Long,
        analyzerConfig: RepositoryAnalyzerConfiguration?,
        excludes: Excludes,
        resolutions: Resolutions,
        curations: Curations,
        packageConfigurations: List<PackageConfiguration>,
        licenseChoices: LicenseChoices
    ): RepositoryConfiguration = db.blockingQuery {
        RepositoryConfigurationDao.new {
            this.ortRun = OrtRunDao[ortRunId]
            this.repositoryAnalyzerConfiguration = analyzerConfig?.let {
                RepositoryAnalyzerConfigurationDao.getOrPut(it)
            }
            this.pathExcludes = SizedCollection(excludes.paths.map(PathExcludeDao::getOrPut))
            this.scopeExcludes = SizedCollection(excludes.scopes.map(ScopeExcludeDao::getOrPut))
            this.issueResolutions = SizedCollection(resolutions.issues.map(IssueResolutionDao::getOrPut))
            this.ruleViolationResolutions =
                SizedCollection(resolutions.ruleViolations.map(RuleViolationResolutionDao::getOrPut))
            this.vulnerabilityResolutions =
                SizedCollection(resolutions.vulnerabilities.map(VulnerabilityResolutionDao::getOrPut))
            this.curations = SizedCollection(curations.packages.map { createPackageCuration(it) })
            this.licenseFindingCurations =
                SizedCollection(curations.licenseFindings.map(LicenseFindingCurationDao::getOrPut))
            this.packageConfigurations = SizedCollection(packageConfigurations.map(PackageConfigurationDao::getOrPut))
            this.spdxLicenseChoices =
                SizedCollection(licenseChoices.repositoryLicenseChoices.map(SpdxLicenseChoiceDao::getOrPut))
            this.packageLicenseChoices =
                SizedCollection(licenseChoices.packageLicenseChoices.map { createPackageLicenseChoice(it) })
        }.mapToModel()
    }

    override fun get(id: Long): RepositoryConfiguration? = db.entityQuery {
        RepositoryConfigurationDao[id].mapToModel()
    }
}

private fun createPackageCuration(packageCuration: PackageCuration): PackageCurationDao =
    PackageCurationDao.new {
        identifier = IdentifierDao.getOrPut(packageCuration.id)
        packageCurationData = PackageCurationDataDao.getOrPut(packageCuration.data)
    }

private fun createPackageLicenseChoice(choice: PackageLicenseChoice): PackageLicenseChoiceDao =
    PackageLicenseChoiceDao.new {
        identifier = IdentifierDao.getOrPut(choice.identifier)
        licenseChoices = SizedCollection(choice.licenseChoices.map(SpdxLicenseChoiceDao::getOrPut))
    }
