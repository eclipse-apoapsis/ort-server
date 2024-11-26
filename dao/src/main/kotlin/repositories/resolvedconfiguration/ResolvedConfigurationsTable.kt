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

package org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration

import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.IssueResolutionDao
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageConfigurationDao
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.RuleViolationResolutionDao
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.VulnerabilityResolutionDao
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.repository.Resolutions

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to represent a [ResolvedConfiguration].
 */
object ResolvedConfigurationsTable : LongIdTable("resolved_configurations") {
    val ortRunId = reference("ort_run_id", OrtRunsTable)
}

class ResolvedConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ResolvedConfigurationDao>(ResolvedConfigurationsTable) {
        fun findByOrtRunId(ortRunId: Long): ResolvedConfigurationDao? =
            find { ResolvedConfigurationsTable.ortRunId eq ortRunId }.limit(1).firstOrNull()

        fun getOrPut(ortRunId: Long): ResolvedConfigurationDao =
            findByOrtRunId(ortRunId) ?: new { this.ortRun = OrtRunDao[ortRunId] }
    }

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
