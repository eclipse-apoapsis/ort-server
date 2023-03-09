/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

import kotlinx.datetime.Instant

import org.jetbrains.exposed.sql.SizedCollection

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.entityQuery
import org.ossreviewtoolkit.server.dao.tables.AdvisorJobDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorConfigurationsOptionDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorResultDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorRunDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorRunIdentifierDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorRunsTable
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.DefectDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.DefectLabelDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.GithubDefectsConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.NexusIqConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.OsvConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.VulnerabilityDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.VulnerabilityReferenceDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.VulnerableCodeConfigurationDao
import org.ossreviewtoolkit.server.dao.utils.getOrPutEnvironment
import org.ossreviewtoolkit.server.dao.utils.getOrPutIdentifier
import org.ossreviewtoolkit.server.dao.utils.getOrPutIssue
import org.ossreviewtoolkit.server.model.repositories.AdvisorRunRepository
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorResult
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun
import org.ossreviewtoolkit.server.model.runs.advisor.Defect
import org.ossreviewtoolkit.server.model.runs.advisor.GithubDefectsConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.NexusIqConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.OsvConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.Vulnerability
import org.ossreviewtoolkit.server.model.runs.advisor.VulnerableCodeConfiguration

/**
 * An implementation of [AdvisorRunRepository] that stores the advisor runs in [AdvisorRunsTable].
 */
class DaoAdvisorRunRepository : AdvisorRunRepository {
    override fun create(
        advisorJobId: Long,
        startTime: Instant,
        endTime: Instant,
        environment: Environment,
        config: AdvisorConfiguration,
        advisorRecords: Map<Identifier, List<AdvisorResult>>
    ): AdvisorRun = blockingQuery {
        val environmentDao = getOrPutEnvironment(environment)

        val advisorRunDao = AdvisorRunDao.new {
            this.advisorJob = AdvisorJobDao[advisorJobId]
            this.startTime = startTime
            this.endTime = endTime
            this.environment = environmentDao
        }

        createAdvisorConfiguration(advisorRunDao, config)

        advisorRecords.forEach { (id, results) ->
            val identifierDao = getOrPutIdentifier(id)

            val advisorRunIdentifierDao = AdvisorRunIdentifierDao.new {
                this.advisorRun = advisorRunDao
                this.identifier = identifierDao
            }

            results.forEach { result ->
                val issues = result.issues.map(::getOrPutIssue)
                val defects = result.defects.map(::getOrPutDefect)
                val vulnerabilities = result.vulnerabilities.map(::getOrPutVulnerability)
                AdvisorResultDao.new {
                    this.advisorRunIdentifier = advisorRunIdentifierDao
                    this.advisorName = result.advisorName
                    this.capabilities = result.capabilities
                    this.startTime = result.startTime
                    this.endTime = result.endTime
                    this.issues = SizedCollection(issues)
                    this.defects = SizedCollection(defects)
                    this.vulnerabilities = SizedCollection(vulnerabilities)
                }
            }
        }

        advisorRunDao.mapToModel()
    }.getOrThrow()

    override fun get(id: Long): AdvisorRun? = entityQuery { AdvisorRunDao[id].mapToModel() }
}

private fun createAdvisorConfiguration(
    advisorRunDao: AdvisorRunDao,
    advisorConfiguration: AdvisorConfiguration
): AdvisorConfigurationDao {
    val advisorConfigurationDao = AdvisorConfigurationDao.new {
        advisorRun = advisorRunDao
        osvConfiguration = advisorConfiguration.osvConfiguration?.let { createOsvConfiguration(it) }
        nexusIqConfiguration = advisorConfiguration.nexusIqConfiguration?.let { createNexusIqConfiguration(it) }
        vulnerableCodeConfiguration = advisorConfiguration.vulnerableCodeConfiguration?.let {
            createVulnerableCodeConfiguration(it)
        }

        githubDefectsConfiguration = advisorConfiguration.githubDefectsConfiguration?.let {
            createGitHubDefectsConfiguration(it)
        }
    }

    advisorConfiguration.options.forEach { (key, value) ->
        AdvisorConfigurationsOptionDao.new {
            this.advisorConfiguration = advisorConfigurationDao
            this.key = key
            this.value = value
        }
    }

    return advisorConfigurationDao
}

fun createVulnerableCodeConfiguration(
    vulnerableCodeConfiguration: VulnerableCodeConfiguration
): VulnerableCodeConfigurationDao =
    VulnerableCodeConfigurationDao.new {
        this.serverUrl = vulnerableCodeConfiguration.serverUrl
    }

fun createNexusIqConfiguration(nexusIqConfiguration: NexusIqConfiguration): NexusIqConfigurationDao =
    NexusIqConfigurationDao.new {
        serverUrl = nexusIqConfiguration.serverUrl
        browseUrl = nexusIqConfiguration.browseUrl
    }

private fun createOsvConfiguration(osvConfiguration: OsvConfiguration): OsvConfigurationDao = OsvConfigurationDao.new {
    this.serverUrl = osvConfiguration.serverUrl
}

private fun createGitHubDefectsConfiguration(
    githubDefectsConfiguration: GithubDefectsConfiguration
): GithubDefectsConfigurationDao = GithubDefectsConfigurationDao.new {
    this.endpointUrl = githubDefectsConfiguration.endpointUrl
    this.labelFilter = githubDefectsConfiguration.labelFilter
    this.maxNumberOfIssuesPerRepository = githubDefectsConfiguration.maxNumberOfIssuesPerRepository
    this.parallelRequests = githubDefectsConfiguration.parallelRequests
}

private fun getOrPutDefect(defect: Defect): DefectDao =
    DefectDao.findByDefect(defect) ?: DefectDao.new {
        externalId = defect.externalId
        url = defect.url
        title = defect.title
        state = defect.state
        severity = defect.severity
        description = defect.description
        creationTime = defect.creationTime
        modificationTime = defect.modificationTime
        closingTime = defect.closingTime
        fixReleaseVersion = defect.fixReleaseVersion
        fixReleaseUrl = defect.fixReleaseUrl
    }.also {
        defect.labels.forEach { (key, value) ->
            DefectLabelDao.new {
                this.defect = it
                this.key = key
                this.value = value
            }
        }
    }

private fun getOrPutVulnerability(vulnerability: Vulnerability): VulnerabilityDao =
    VulnerabilityDao.findByVulnerability(vulnerability) ?: VulnerabilityDao.new {
        externalId = vulnerability.externalId
        summary = vulnerability.summary
        description = vulnerability.description
    }.also {
        vulnerability.references.forEach { reference ->
            VulnerabilityReferenceDao.new {
                this.vulnerability = it
                this.url = reference.url
                this.scoringSystem = reference.scoringSystem
                this.severity = reference.severity
            }
        }
    }
