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

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.mapAndDeduplicate
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryConfigurationRepository
import org.eclipse.apoapsis.ortserver.model.runs.repository.Curations
import org.eclipse.apoapsis.ortserver.model.runs.repository.Excludes
import org.eclipse.apoapsis.ortserver.model.runs.repository.LicenseChoices
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCuration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageLicenseChoice
import org.eclipse.apoapsis.ortserver.model.runs.repository.ProvenanceSnippetChoices
import org.eclipse.apoapsis.ortserver.model.runs.repository.RepositoryAnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.repository.RepositoryConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.repository.Resolutions

import org.jetbrains.exposed.sql.Database

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
        licenseChoices: LicenseChoices,
        provenanceSnippetChoices: List<ProvenanceSnippetChoices>
    ): RepositoryConfiguration = db.blockingQuery {
        RepositoryConfigurationDao.new {
            this.ortRun = OrtRunDao[ortRunId]
            this.repositoryAnalyzerConfiguration = analyzerConfig?.let {
                RepositoryAnalyzerConfigurationDao.getOrPut(it)
            }
            this.pathExcludes = mapAndDeduplicate(excludes.paths, PathExcludeDao::getOrPut)
            this.scopeExcludes = mapAndDeduplicate(excludes.scopes, ScopeExcludeDao::getOrPut)
            this.issueResolutions = mapAndDeduplicate(resolutions.issues, IssueResolutionDao::getOrPut)
            this.ruleViolationResolutions =
                mapAndDeduplicate(resolutions.ruleViolations, RuleViolationResolutionDao::getOrPut)
            this.vulnerabilityResolutions =
                mapAndDeduplicate(resolutions.vulnerabilities, VulnerabilityResolutionDao::getOrPut)
            this.curations = mapAndDeduplicate(curations.packages, ::createPackageCuration)
            this.licenseFindingCurations =
                mapAndDeduplicate(curations.licenseFindings, LicenseFindingCurationDao::getOrPut)
            this.packageConfigurations = mapAndDeduplicate(packageConfigurations, PackageConfigurationDao::getOrPut)
            this.spdxLicenseChoices =
                mapAndDeduplicate(licenseChoices.repositoryLicenseChoices, SpdxLicenseChoiceDao::getOrPut)
            this.packageLicenseChoices =
                mapAndDeduplicate(licenseChoices.packageLicenseChoices, ::createPackageLicenseChoice)
            this.provenanceSnippetChoices = mapAndDeduplicate(provenanceSnippetChoices, SnippetChoicesDao::getOrPut)
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
        licenseChoices = mapAndDeduplicate(choice.licenseChoices, SpdxLicenseChoiceDao::getOrPut)
    }
