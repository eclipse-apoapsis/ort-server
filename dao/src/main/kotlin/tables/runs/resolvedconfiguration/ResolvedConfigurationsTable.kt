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

package org.ossreviewtoolkit.server.dao.tables.resolvedconfiguration

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

import org.ossreviewtoolkit.server.dao.tables.OrtRunDao
import org.ossreviewtoolkit.server.dao.tables.OrtRunsTable
import org.ossreviewtoolkit.server.dao.tables.runs.repository.IssueResolutionDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.PackageConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.RuleViolationResolutionDao
import org.ossreviewtoolkit.server.dao.tables.runs.repository.VulnerabilityResolutionDao
import org.ossreviewtoolkit.server.model.resolvedconfiguration.ResolvedConfiguration
import org.ossreviewtoolkit.server.model.runs.repository.Resolutions

/**
 * A table to represent a [ResolvedConfiguration].
 */
object ResolvedConfigurationsTable : LongIdTable("resolved_configurations") {
    val ortRunId = reference("ort_run_id", OrtRunsTable)
}

class ResolvedConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ResolvedConfigurationDao>(ResolvedConfigurationsTable)

    var ortRun by OrtRunDao referencedOn ResolvedConfigurationsTable.ortRunId

    val packageConfigurations by PackageConfigurationDao via ResolvedConfigurationsPackageConfigurationsTable
    val packageCurationProviders by ResolvedPackageCurationProviderDao referrersOn
            ResolvedPackageCurationProvidersTable.resolvedConfigurationId
    val issueResolutions by IssueResolutionDao via ResolvedConfigurationsIssueResolutionsTable
    val ruleViolationResolutions by RuleViolationResolutionDao via ResolvedConfigurationsRuleViolationResolutionsTable
    val vulnerabilityResolutions by VulnerabilityResolutionDao via ResolvedConfigurationsVulnerabilityResolutionsTable

    fun mapToModel() = ResolvedConfiguration(
        packageConfigurations = packageConfigurations.map { it.mapToModel() },
        // TODO: For simplicity the providers are currently sorted in memory, but ideally this should be done by the
        //       database already. Also see: https://github.com/JetBrains/Exposed/issues/1362
        packageCurations = packageCurationProviders.sortedBy { it.rank }.map { it.mapToModel() },
        resolutions = Resolutions(
            issues = issueResolutions.map { it.mapToModel() },
            ruleViolations = ruleViolationResolutions.map { it.mapToModel() },
            vulnerabilities = vulnerabilityResolutions.map { it.mapToModel() }
        )
    )
}
