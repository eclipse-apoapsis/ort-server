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

package org.eclipse.apoapsis.ortserver.dao.repositories.ortrun

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.blockingQueryCatching
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.mapAndDeduplicate
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunIssueDao
import org.eclipse.apoapsis.ortserver.dao.utils.applyFilter
import org.eclipse.apoapsis.ortserver.dao.utils.listQuery
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunFilters
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.OrtRunSummary
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.delete
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.max

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DaoOrtRunRepository::class.java)

class DaoOrtRunRepository(private val db: Database) : OrtRunRepository {
    override fun create(
        repositoryId: Long,
        revision: String,
        path: String?,
        jobConfigs: JobConfigurations,
        jobConfigContext: String?,
        labels: Map<String, String>,
        traceId: String?,
        environmentConfigPath: String?
    ): OrtRun = db.blockingQuery {
        val maxIndex = OrtRunsTable.index.max()
        val lastIndex = OrtRunsTable
            .select(maxIndex)
            .where { OrtRunsTable.repositoryId eq repositoryId }
            .singleOrNull()
            ?.get(maxIndex)

        val nextIndex = (lastIndex ?: 0) + 1

        OrtRunDao.new {
            this.index = nextIndex
            this.repositoryId = repositoryId
            this.revision = revision
            this.path = path
            this.createdAt = Clock.System.now().toDatabasePrecision()
            this.jobConfigs = jobConfigs
            this.jobConfigContext = jobConfigContext
            this.status = OrtRunStatus.CREATED
            this.labels = mapAndDeduplicate(labels.entries, ::getLabelDao)
            this.traceId = traceId
            this.environmentConfigPath = environmentConfigPath
        }.mapToModel()
    }

    override fun get(id: Long): OrtRun? = db.entityQuery { OrtRunDao[id].mapToModel() }

    override fun getByIndex(repositoryId: Long, ortRunIndex: Long): OrtRun? = db.blockingQuery {
        OrtRunDao.find { OrtRunsTable.repositoryId eq repositoryId and (OrtRunsTable.index eq ortRunIndex) }
            .firstOrNull()?.mapToModel()
    }

    override fun getIdByIndex(repositoryId: Long, ortRunIndex: Long): Long? = db.blockingQuery {
        OrtRunDao.find { OrtRunsTable.repositoryId eq repositoryId and (OrtRunsTable.index eq ortRunIndex) }
            .firstOrNull()?.id?.value
    }

    override fun list(parameters: ListQueryParameters, filters: OrtRunFilters?): ListQueryResult<OrtRun> =
        db.blockingQuery {
            OrtRunDao.listQuery(parameters, OrtRunDao::mapToModel) {
                var condition: Op<Boolean> = Op.TRUE

                filters?.status?.let { statusFilter ->
                    condition = condition and OrtRunsTable.status.applyFilter(
                        statusFilter.operator,
                        statusFilter.value
                    )
                }

                condition
            }
        }

    override fun listForRepository(repositoryId: Long, parameters: ListQueryParameters): ListQueryResult<OrtRun> =
        db.blockingQueryCatching {
            OrtRunDao.listQuery(parameters, OrtRunDao::mapToModel) { OrtRunsTable.repositoryId eq repositoryId }
        }.getOrElse {
            logger.error("Cannot list ORT runs for repository $repositoryId.", it)
            ListQueryResult(emptyList(), parameters, 0L)
        }

    override fun listSummariesForRepository(
        repositoryId: Long,
        parameters: ListQueryParameters
    ): ListQueryResult<OrtRunSummary> =
        db.blockingQueryCatching {
            OrtRunDao.listQuery(parameters, OrtRunDao::mapToSummaryModel) { OrtRunsTable.repositoryId eq repositoryId }
        }.getOrElse {
            logger.error("Cannot list ORT runs for repository $repositoryId.", it)
            ListQueryResult(emptyList(), parameters, 0L)
        }

    override fun update(
        id: Long,
        status: OptionalValue<OrtRunStatus>,
        jobConfigs: OptionalValue<JobConfigurations>,
        resolvedJobConfigs: OptionalValue<JobConfigurations>,
        resolvedJobConfigContext: OptionalValue<String?>,
        issues: OptionalValue<Collection<Issue>>,
        labels: OptionalValue<Map<String, String>>
    ): OrtRun = db.blockingQuery {
        val ortRun = OrtRunDao[id]

        status.ifPresent {
            ortRun.status = it

            if (it.completed) {
                ortRun.finishedAt = Clock.System.now().toDatabasePrecision()
            }
        }

        jobConfigs.ifPresent { ortRun.jobConfigs = it }

        resolvedJobConfigs.ifPresent { ortRun.resolvedJobConfigs = it }
        resolvedJobConfigContext.ifPresent { ortRun.resolvedJobConfigContext = it }

        issues.ifPresent { issues ->
            issues.forEach { OrtRunIssueDao.createByIssue(id, it) }
        }

        labels.ifPresent { labels ->
            val newLabels = mutableSetOf<LabelDao>()
            newLabels += ortRun.labels
            newLabels += labels.map(::getLabelDao)
            ortRun.labels = SizedCollection(newLabels)
        }

        OrtRunDao[id].mapToModel()
    }

    override fun delete(id: Long): Int = db.blockingQuery {
        OrtRunsTable.deleteWhere { OrtRunsTable.id eq id }
    }

    override fun deleteByRepository(repositoryId: Long): Int = db.blockingQuery {
        OrtRunsTable.deleteWhere { OrtRunsTable.repositoryId eq repositoryId }
    }

    override fun deleteByProduct(productId: Long): Int = db.blockingQuery {
        OrtRunsTable
            .innerJoin(RepositoriesTable, { repositoryId }, { RepositoriesTable.id })
            .innerJoin(ProductsTable, { RepositoriesTable.productId }, { ProductsTable.id })
            .delete(OrtRunsTable) {
                ProductsTable.id eq productId
            }
    }
}

/**
 * Obtain a [LabelDao] for the given [entry].
 */
private fun getLabelDao(entry: Map.Entry<String, String>): LabelDao =
    LabelDao.getOrPut(entry.key, entry.value)
