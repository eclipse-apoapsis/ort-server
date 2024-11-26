/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.mapAndDeduplicate
import org.eclipse.apoapsis.ortserver.dao.tables.AdvisorJobDao
import org.eclipse.apoapsis.ortserver.dao.tables.OrtRunIssueDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.advisor.AdvisorConfigurationDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.advisor.AdvisorConfigurationOptionDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.advisor.AdvisorConfigurationSecretDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.advisor.AdvisorConfigurationsOptionsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.advisor.AdvisorConfigurationsSecretsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.advisor.AdvisorResultDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.advisor.AdvisorRunDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.advisor.AdvisorRunIdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.advisor.AdvisorRunsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.advisor.DefectDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.advisor.VulnerabilityDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.EnvironmentDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorRunRepository
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorResult
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorRun

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert

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
        results: Map<Identifier, List<AdvisorResult>>
    ): AdvisorRun = db.blockingQuery {
        val environmentDao = EnvironmentDao.getOrPut(environment)
        val advisorJobDao = AdvisorJobDao[advisorJobId]

        val advisorRunDao = AdvisorRunDao.new {
            this.advisorJob = advisorJobDao
            this.startTime = startTime
            this.endTime = endTime
            this.environment = environmentDao
        }

        createAdvisorConfiguration(advisorRunDao, config)

        results.forEach { (id, advisorResults) ->
            val identifierDao = IdentifierDao.getOrPut(id)

            val advisorRunIdentifierDao = AdvisorRunIdentifierDao.new {
                this.advisorRun = advisorRunDao
                this.identifier = identifierDao
            }

            advisorResults.forEach { result ->
                val defects = mapAndDeduplicate(result.defects, DefectDao::getOrPut)
                val vulnerabilities = mapAndDeduplicate(result.vulnerabilities, VulnerabilityDao::getOrPut)
                AdvisorResultDao.new {
                    this.advisorRunIdentifier = advisorRunIdentifierDao
                    this.advisorName = result.advisorName
                    this.capabilities = result.capabilities
                    this.startTime = result.startTime
                    this.endTime = result.endTime
                    this.defects = defects
                    this.vulnerabilities = vulnerabilities
                }

                result.issues.forEach { issue ->
                    OrtRunIssueDao.createByIssue(
                        advisorJobDao.ortRun.id.value,
                        issue.copy(identifier = id, worker = AdvisorRunDao.ISSUE_WORKER_TYPE)
                    )
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
