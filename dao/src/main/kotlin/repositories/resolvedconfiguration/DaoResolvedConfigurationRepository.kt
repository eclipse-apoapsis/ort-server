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

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.AdvisorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorResultsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorResultsVulnerabilitiesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorRunsIdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.ResolvedVulnerabilitiesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.VulnerabilitiesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.EvaluatorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.EvaluatorRunsRuleViolationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.EvaluatorRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.ResolvedRuleViolationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.RuleViolationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.IssueResolutionDao
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageConfigurationDao
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDao
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataDao
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.RuleViolationResolutionDao
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.VulnerabilityResolutionDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunsIssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.ResolvedIssuesTable
import org.eclipse.apoapsis.ortserver.dao.utils.DigestFunction
import org.eclipse.apoapsis.ortserver.model.repositories.ResolvedConfigurationRepository
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedConfiguration
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedItemsResult
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedPackageCurations
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.RuleViolation
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Vulnerability
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageConfiguration

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.upsert

class DaoResolvedConfigurationRepository(private val db: Database) : ResolvedConfigurationRepository {
    override fun get(id: Long): ResolvedConfiguration? = db.entityQuery { ResolvedConfigurationDao[id].mapToModel() }

    override fun getForOrtRun(ortRunId: Long): ResolvedConfiguration? = db.blockingQuery {
        ResolvedConfigurationDao.find { ResolvedConfigurationsTable.ortRunId eq ortRunId }.limit(1)
            .firstOrNull()?.mapToModel()
    }

    override fun addPackageConfigurations(ortRunId: Long, packageConfigurations: List<PackageConfiguration>) =
        db.blockingQuery {
            val resolvedConfiguration = ResolvedConfigurationDao.getOrPut(ortRunId)
            packageConfigurations.forEach { packageConfiguration ->
                val packageConfigurationDao = PackageConfigurationDao.getOrPut(packageConfiguration)
                ResolvedConfigurationsPackageConfigurationsTable.insert {
                    it[resolvedConfigurationId] = resolvedConfiguration.id
                    it[packageConfigurationId] = packageConfigurationDao.id
                }
            }
        }

    override fun addPackageCurations(ortRunId: Long, packageCurations: List<ResolvedPackageCurations>) =
        db.blockingQuery {
            val resolvedConfiguration = ResolvedConfigurationDao.getOrPut(ortRunId)
            val providerOffset = resolvedConfiguration.packageCurationProviders.count().toInt()
            packageCurations.forEachIndexed { index, resolvedPackageCurations ->
                val packageCurationProviderConfig =
                    PackageCurationProviderConfigDao.getOrPut(resolvedPackageCurations.provider)

                val resolvedPackageCurationProvider = ResolvedPackageCurationProviderDao.new {
                    this.packageCurationProviderConfig = packageCurationProviderConfig
                    this.resolvedConfiguration = resolvedConfiguration
                    this.rank = providerOffset + index
                }

                resolvedPackageCurations.curations.forEachIndexed { curationIndex, packageCuration ->
                    val packageCurationDao = PackageCurationDao.new {
                        identifier = IdentifierDao.getOrPut(packageCuration.id)
                        packageCurationData = PackageCurationDataDao.getOrPut(packageCuration.data)
                    }

                    ResolvedPackageCurationDao.new {
                        this.resolvedPackageCurationProvider = resolvedPackageCurationProvider
                        this.packageCuration = packageCurationDao
                        this.rank = curationIndex
                    }
                }
            }
        }

    override fun addResolutions(ortRunId: Long, resolvedItems: ResolvedItemsResult) = db.blockingQuery {
        val resolvedConfiguration = ResolvedConfigurationDao.getOrPut(ortRunId)

        // Store unique issue resolutions and the mappings between issues and their resolutions
        resolvedItems.issues.forEach { (issue, resolutions) ->
            val ortRunIssueId = findOrtRunIssueId(ortRunId, issue)
            resolutions.forEach { resolution ->
                val issueResolutionDao = IssueResolutionDao.getOrPut(resolution)

                // Store in resolved configuration (unique resolutions)
                ResolvedConfigurationsIssueResolutionsTable.upsert(
                    ResolvedConfigurationsIssueResolutionsTable.resolvedConfigurationId,
                    ResolvedConfigurationsIssueResolutionsTable.issueResolutionId
                ) {
                    it[resolvedConfigurationId] = resolvedConfiguration.id
                    it[issueResolutionId] = issueResolutionDao.id
                }

                // Store resolved item mapping if the issue was found in the run
                if (ortRunIssueId != null) {
                    ResolvedIssuesTable.insert {
                        it[ResolvedIssuesTable.ortRunId] = ortRunId
                        it[ResolvedIssuesTable.ortRunIssueId] = ortRunIssueId
                        it[issueResolutionId] = issueResolutionDao.id.value
                    }
                }
            }
        }

        // Store unique rule violation resolutions and the mappings
        resolvedItems.ruleViolations.forEach { (violation, resolutions) ->
            val ruleViolationId = findRuleViolationId(ortRunId, violation)
            resolutions.forEach { resolution ->
                val ruleViolationResolutionDao = RuleViolationResolutionDao.getOrPut(resolution)

                // Store in resolved configuration (unique resolutions)
                ResolvedConfigurationsRuleViolationResolutionsTable.upsert(
                    ResolvedConfigurationsRuleViolationResolutionsTable.resolvedConfigurationId,
                    ResolvedConfigurationsRuleViolationResolutionsTable.ruleViolationResolutionId
                ) {
                    it[resolvedConfigurationId] = resolvedConfiguration.id
                    it[ruleViolationResolutionId] = ruleViolationResolutionDao.id
                }

                // Store resolved item mapping if the rule violation was found
                if (ruleViolationId != null) {
                    ResolvedRuleViolationsTable.insert {
                        it[ResolvedRuleViolationsTable.ortRunId] = ortRunId
                        it[ResolvedRuleViolationsTable.ruleViolationId] = ruleViolationId
                        it[ruleViolationResolutionId] = ruleViolationResolutionDao.id.value
                    }
                }
            }
        }

        // Store unique vulnerability resolutions and the mappings
        resolvedItems.vulnerabilities.forEach { (vulnerability, resolutions) ->
            val vulnerabilityIdentifierPairs = findVulnerabilityIdentifierPairs(ortRunId, vulnerability)
            resolutions.forEach { resolution ->
                val vulnerabilityResolutionDao = VulnerabilityResolutionDao.getOrPut(resolution)

                // Store in resolved configuration (unique resolutions)
                ResolvedConfigurationsVulnerabilityResolutionsTable.upsert(
                    ResolvedConfigurationsVulnerabilityResolutionsTable.resolvedConfigurationId,
                    ResolvedConfigurationsVulnerabilityResolutionsTable.vulnerabilityResolutionId
                ) {
                    it[resolvedConfigurationId] = resolvedConfiguration.id
                    it[vulnerabilityResolutionId] = vulnerabilityResolutionDao.id
                }

                // Store resolved item mappings for each vulnerability-identifier pair
                vulnerabilityIdentifierPairs.forEach { (vulnId, identifierId) ->
                    ResolvedVulnerabilitiesTable.insert {
                        it[ResolvedVulnerabilitiesTable.ortRunId] = ortRunId
                        it[vulnerabilityId] = vulnId
                        it[ResolvedVulnerabilitiesTable.identifierId] = identifierId
                        it[vulnerabilityResolutionId] = vulnerabilityResolutionDao.id.value
                    }
                }
            }
        }
    }

    /**
     * Find the OrtRunIssue ID for a given issue in the context of an ORT run.
     * Matches by message, timestamp, source, severity, and affectedPath.
     */
    private fun findOrtRunIssueId(ortRunId: Long, issue: Issue): Long? {
        return OrtRunsIssuesTable
            .innerJoin(IssuesTable)
            .select(OrtRunsIssuesTable.id)
            .where {
                (OrtRunsIssuesTable.ortRunId eq ortRunId) and
                    (IssuesTable.issueSource eq issue.source) and
                    (DigestFunction(IssuesTable.message) eq DigestFunction(stringLiteral(issue.message))) and
                    (IssuesTable.severity eq issue.severity) and
                    (IssuesTable.affectedPath eq issue.affectedPath)
            }
            .firstOrNull()?.get(OrtRunsIssuesTable.id)?.value
    }

    /**
     * Find the RuleViolation ID for a given rule violation in the context of an ORT run.
     * Matches by rule, message, severity, license, licenseSources, and identifier through the evaluator run chain.
     */
    private fun findRuleViolationId(ortRunId: Long, violation: RuleViolation): Long? {
        val identifierDao = violation.id?.let { IdentifierDao.findByIdentifier(it) }
        val licenseSourcesStr = violation.licenseSources.takeIf { it.isNotEmpty() }?.joinToString(",")

        return RuleViolationsTable
            .innerJoin(EvaluatorRunsRuleViolationsTable)
            .innerJoin(EvaluatorRunsTable)
            .innerJoin(EvaluatorJobsTable)
            .select(RuleViolationsTable.id, RuleViolationsTable.message, RuleViolationsTable.howToFix)
            .where {
                (EvaluatorJobsTable.ortRunId eq ortRunId) and
                    (RuleViolationsTable.rule eq violation.rule) and
                    (RuleViolationsTable.severity eq violation.severity) and
                    (RuleViolationsTable.identifierId eq identifierDao?.id) and
                    (RuleViolationsTable.license eq violation.license) and
                    (RuleViolationsTable.licenseSources eq licenseSourcesStr)
            }
            .firstOrNull { row ->
                // Additional filtering that's harder to do in SQL due to text comparison
                row[RuleViolationsTable.message] == violation.message &&
                    row[RuleViolationsTable.howToFix] == violation.howToFix
            }?.get(RuleViolationsTable.id)?.value
    }

    /**
     * Find all (vulnerability_id, identifier_id) pairs for a given vulnerability in the context of an ORT run.
     * A vulnerability can be associated with multiple identifiers (packages).
     */
    private fun findVulnerabilityIdentifierPairs(
        ortRunId: Long,
        vulnerability: Vulnerability
    ): List<Pair<Long, Long>> {
        return VulnerabilitiesTable
            .innerJoin(AdvisorResultsVulnerabilitiesTable)
            .innerJoin(AdvisorResultsTable)
            .innerJoin(AdvisorRunsIdentifiersTable)
            .innerJoin(AdvisorRunsTable)
            .innerJoin(AdvisorJobsTable)
            .select(VulnerabilitiesTable.id, AdvisorRunsIdentifiersTable.identifierId)
            .where {
                (AdvisorJobsTable.ortRunId eq ortRunId) and
                    (VulnerabilitiesTable.externalId eq vulnerability.externalId)
            }
            .map { row ->
                Pair(
                    row[VulnerabilitiesTable.id].value,
                    row[AdvisorRunsIdentifiersTable.identifierId].value
                )
            }
            .distinct()
    }
}
