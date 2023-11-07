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

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.entityQuery
import org.ossreviewtoolkit.server.dao.mapAndDeduplicate
import org.ossreviewtoolkit.server.dao.tables.AdvisorJobDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorConfigurationOptionDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorConfigurationSecretDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorConfigurationsOptionsTable
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorConfigurationsSecretsTable
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorResultDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorRunDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorRunIdentifierDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.AdvisorRunsTable
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.DefectDao
import org.ossreviewtoolkit.server.dao.tables.runs.advisor.VulnerabilityDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifierDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.OrtIssueDao
import org.ossreviewtoolkit.server.model.repositories.AdvisorRunRepository
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorResult
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun

/**
 * An implementation of [AdvisorRunRepository] that stores the advisor runs in [AdvisorRunsTable].
 */
class DaoAdvisorRunRepository(private val db: Database) : AdvisorRunRepository {
    override fun create(
        advisorJobId: Long,
        startTime: Instant,
        endTime: Instant,
        environment: Environment,
        config: AdvisorConfiguration,
        advisorRecords: Map<Identifier, List<AdvisorResult>>
    ): AdvisorRun = db.blockingQuery {
        val environmentDao = EnvironmentDao.getOrPut(environment)

        val advisorRunDao = AdvisorRunDao.new {
            this.advisorJob = AdvisorJobDao[advisorJobId]
            this.startTime = startTime
            this.endTime = endTime
            this.environment = environmentDao
        }

        createAdvisorConfiguration(advisorRunDao, config)

        advisorRecords.forEach { (id, results) ->
            val identifierDao = IdentifierDao.getOrPut(id)

            val advisorRunIdentifierDao = AdvisorRunIdentifierDao.new {
                this.advisorRun = advisorRunDao
                this.identifier = identifierDao
            }

            results.forEach { result ->
                val issues = mapAndDeduplicate(result.issues, OrtIssueDao::getOrPut)
                val defects = mapAndDeduplicate(result.defects, DefectDao::getOrPut)
                val vulnerabilities = mapAndDeduplicate(result.vulnerabilities, VulnerabilityDao::getOrPut)
                AdvisorResultDao.new {
                    this.advisorRunIdentifier = advisorRunIdentifierDao
                    this.advisorName = result.advisorName
                    this.capabilities = result.capabilities
                    this.startTime = result.startTime
                    this.endTime = result.endTime
                    this.issues = issues
                    this.defects = defects
                    this.vulnerabilities = vulnerabilities
                }
            }
        }

        advisorRunDao.mapToModel()
    }

    override fun get(id: Long): AdvisorRun? = db.entityQuery { AdvisorRunDao[id].mapToModel() }

    override fun getByJobId(advisorJobId: Long): AdvisorRun? = db.entityQuery {
        AdvisorRunDao.find { AdvisorRunsTable.advisorJobId eq advisorJobId }.firstOrNull()?.mapToModel()
    }
}

private fun createAdvisorConfiguration(
    advisorRunDao: AdvisorRunDao,
    advisorConfiguration: AdvisorConfiguration
): AdvisorConfigurationDao {
    val advisorConfigurationDao = AdvisorConfigurationDao.new {
        advisorRun = advisorRunDao
    }

    advisorConfiguration.config.forEach { (advisor, pluginConfig) ->
        pluginConfig.options.forEach { (option, value) ->
            val optionDao = AdvisorConfigurationOptionDao.getOrPut(advisor, option, value)

            AdvisorConfigurationsOptionsTable.insert {
                it[advisorConfigurationId] = advisorConfigurationDao.id
                it[advisorConfigurationOptionId] = optionDao.id
            }
        }

        pluginConfig.secrets.forEach { (secret, value) ->
            val secretDao = AdvisorConfigurationSecretDao.getOrPut(advisor, secret, value)

            AdvisorConfigurationsSecretsTable.insert {
                it[advisorConfigurationId] = advisorConfigurationDao.id
                it[advisorConfigurationSecretId] = secretDao.id
            }
        }
    }

    return advisorConfigurationDao
}
