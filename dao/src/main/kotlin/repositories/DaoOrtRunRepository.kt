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

package org.eclipse.apoapsis.ortserver.dao.repositories

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.blockingQueryCatching
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.mapAndDeduplicate
import org.eclipse.apoapsis.ortserver.dao.tables.LabelDao
import org.eclipse.apoapsis.ortserver.dao.tables.OrtRunDao
import org.eclipse.apoapsis.ortserver.dao.tables.OrtRunsLabelsTable
import org.eclipse.apoapsis.ortserver.dao.tables.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.tables.RepositoryDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.IssueDao
import org.eclipse.apoapsis.ortserver.dao.utils.apply
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere

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
        issues: Collection<Issue>
    ): OrtRun = db.blockingQuery {
        val nextIndex = (listForRepository(repositoryId).maxByOrNull { it.index }?.index ?: 0) + 1

        OrtRunDao.new {
            this.index = nextIndex
            this.repository = RepositoryDao[repositoryId]
            this.revision = revision
            this.path = path
            this.createdAt = Clock.System.now().toDatabasePrecision()
            this.jobConfigs = jobConfigs
            this.jobConfigContext = jobConfigContext
            this.status = OrtRunStatus.CREATED
            this.labels = mapAndDeduplicate(labels.entries, ::getLabelDao)
            this.issues = mapAndDeduplicate(issues, IssueDao::createByIssue)
        }.mapToModel()
    }

    override fun get(id: Long): OrtRun? = db.entityQuery { OrtRunDao[id].mapToModel() }

    override fun getByIndex(repositoryId: Long, ortRunIndex: Long): OrtRun? = db.blockingQuery {
        OrtRunDao.find { OrtRunsTable.repositoryId eq repositoryId and (OrtRunsTable.index eq ortRunIndex) }
            .firstOrNull()?.mapToModel()
    }

    override fun listForRepository(repositoryId: Long, parameters: ListQueryParameters): List<OrtRun> =
        db.blockingQueryCatching {
            OrtRunDao.find { OrtRunsTable.repositoryId eq repositoryId }
                .apply(OrtRunsTable, parameters)
                .map { it.mapToModel() }
        }.getOrElse {
            logger.error("Cannot list ORT runs for repository $repositoryId.", it)
            emptyList()
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
            ortRun.issues = SizedCollection(ortRun.issues + mapAndDeduplicate(issues, IssueDao::createByIssue))
        }

        labels.ifPresent { labels ->
            val newLabels = mutableSetOf<LabelDao>()
            newLabels += ortRun.labels
            newLabels += labels.map(::getLabelDao)
            ortRun.labels = SizedCollection(newLabels)
        }

        OrtRunDao[id].mapToModel()
    }

    override fun delete(id: Long) = db.blockingQuery {
        OrtRunsLabelsTable.deleteWhere { ortRunId eq id }
        OrtRunDao[id].delete()
    }
}

/**
 * Obtain a [LabelDao] for the given [entry].
 */
private fun getLabelDao(entry: Map.Entry<String, String>): LabelDao =
    LabelDao.getOrPut(entry.key, entry.value)
