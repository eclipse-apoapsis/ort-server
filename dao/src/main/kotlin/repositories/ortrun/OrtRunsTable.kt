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

import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.AdvisorJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.AdvisorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.EvaluatorJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.EvaluatorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.notifierjob.NotifierJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.notifierjob.NotifierJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob.ReporterJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob.ReporterJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoryDao
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.RepositoryConfigurationDao
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.RepositoryConfigurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.ScannerJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.ScannerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.userDisplayName.UserDisplayNameDAO
import org.eclipse.apoapsis.ortserver.dao.repositories.userDisplayName.UserDisplayNamesTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedRepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedRepositoryDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunIssueDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunsIssuesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.dao.utils.SortableEntityClass
import org.eclipse.apoapsis.ortserver.dao.utils.SortableTable
import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.dao.utils.transformToEntityId
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobSummaries
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.OrtRunSummary

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * A table to represent an ORT run.
 */
object OrtRunsTable : SortableTable("ort_runs") {
    val repositoryId = reference("repository_id", RepositoriesTable)

    val index = long("index").sortable()
    val revision = text("revision").sortable()
    val resolvedRevision = text("resolved_revision").nullable()
    val createdAt = timestamp("created_at").sortable("createdAt")

    // TODO: Create a proper database representation for configurations, JSON is only used because of the expected
    //       frequent changes during early development.
    val jobConfigs = jsonb<JobConfigurations>("job_configs")
    val resolvedJobConfigs = jsonb<JobConfigurations>("resolved_job_configs").nullable()
    val jobConfigContext = text("job_config_context").nullable()
    val resolvedJobConfigContext = text("resolved_job_config_context").nullable()
    val vcsId = reference("vcs_id", VcsInfoTable).nullable()
    val vcsProcessedId = reference("vcs_processed_id", VcsInfoTable).nullable()
    val status = enumerationByName<OrtRunStatus>("status", 128)
    val finishedAt = timestamp("finished_at").nullable()
    val path = text("path").nullable()
    val traceId = text("trace_id").nullable()
    val environmentConfigPath = text("environment_config_path").nullable()
    val userDisplayName = reference("user_id", UserDisplayNamesTable.id).nullable()
}

class OrtRunDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : SortableEntityClass<OrtRunDao>(OrtRunsTable)

    var repositoryId by OrtRunsTable.repositoryId.transformToEntityId()
    var repository by RepositoryDao referencedOn OrtRunsTable.repositoryId

    var index by OrtRunsTable.index
    var revision by OrtRunsTable.revision
    var resolvedRevision by OrtRunsTable.resolvedRevision
    var path by OrtRunsTable.path
    var traceId by OrtRunsTable.traceId
    var createdAt by OrtRunsTable.createdAt.transformToDatabasePrecision()
    var jobConfigs by OrtRunsTable.jobConfigs
    var resolvedJobConfigs by OrtRunsTable.resolvedJobConfigs
    var jobConfigContext by OrtRunsTable.jobConfigContext
    var resolvedJobConfigContext by OrtRunsTable.resolvedJobConfigContext
    var status by OrtRunsTable.status
    var finishedAt by OrtRunsTable.finishedAt.transformToDatabasePrecision()
    val issues by OrtRunIssueDao referrersOn OrtRunsIssuesTable.ortRunId
    var labels by LabelDao via OrtRunsLabelsTable
    var vcsId by OrtRunsTable.vcsId
    var vcsProcessedId by OrtRunsTable.vcsProcessedId
    var environmentConfigPath by OrtRunsTable.environmentConfigPath
    var userDisplayName by UserDisplayNameDAO optionalReferencedOn OrtRunsTable.userDisplayName

    val advisorJob by AdvisorJobDao optionalBackReferencedOn AdvisorJobsTable.ortRunId
    val analyzerJob by AnalyzerJobDao optionalBackReferencedOn AnalyzerJobsTable.ortRunId
    val evaluatorJob by EvaluatorJobDao optionalBackReferencedOn EvaluatorJobsTable.ortRunId
    val scannerJob by ScannerJobDao optionalBackReferencedOn ScannerJobsTable.ortRunId
    val reporterJob by ReporterJobDao optionalBackReferencedOn ReporterJobsTable.ortRunId
    val notifierJob by NotifierJobDao optionalBackReferencedOn NotifierJobsTable.ortRunId
    val repositoryConfig by RepositoryConfigurationDao optionalBackReferencedOn RepositoryConfigurationsTable.ortRunId
    val nestedRepositories by NestedRepositoryDao referrersOn NestedRepositoriesTable.ortRunId

    fun mapToModel() = OrtRun(
        id = id.value,
        index = index,
        organizationId = repository.product.organization.id.value,
        productId = repository.product.id.value,
        repositoryId = repository.id.value,
        revision = revision,
        resolvedRevision = resolvedRevision,
        path = path,
        createdAt = createdAt,
        jobConfigs = jobConfigs,
        resolvedJobConfigs = resolvedJobConfigs,
        status = status,
        finishedAt = finishedAt,
        labels = labels.associate { it.mapToModel() },
        vcsId = vcsId?.value,
        vcsProcessedId = vcsProcessedId?.value,
        nestedRepositoryIds = nestedRepositories.associate { it.path to it.vcsId.value },
        repositoryConfigId = repositoryConfig?.id?.value,
        issues = issues.map { it.mapToModel() },
        jobConfigContext = jobConfigContext,
        resolvedJobConfigContext = resolvedJobConfigContext,
        traceId = traceId,
        environmentConfigPath = environmentConfigPath,
        userDisplayName = userDisplayName?.mapToModel(),
    )

    /**
     * Map this [OrtRunDao] to an [OrtRunSummary] which is a reduced representation of an [OrtRun].
     * This prevents unnecessary loading of related data from the database.
     */
    fun mapToSummaryModel(): OrtRunSummary {
        val jobSummaries = JobSummaries(
            advisor = advisorJob?.mapToJobSummaryModel(),
            analyzer = analyzerJob?.mapToJobSummaryModel(),
            evaluator = evaluatorJob?.mapToJobSummaryModel(),
            scanner = scannerJob?.mapToJobSummaryModel(),
            reporter = reporterJob?.mapToJobSummaryModel()
        )

        return OrtRunSummary(
            id = id.value,
            index = index,
            organizationId = repository.product.organization.id.value,
            productId = repository.product.id.value,
            repositoryId = repository.id.value,
            revision = revision,
            resolvedRevision = resolvedRevision,
            path = path,
            createdAt = createdAt,
            finishedAt = finishedAt,
            jobs = jobSummaries,
            status = status,
            labels = labels.associate { it.mapToModel() },
            jobConfigContext = jobConfigContext,
            resolvedJobConfigContext = resolvedJobConfigContext,
            environmentConfigPath = environmentConfigPath,
            userDisplayName = userDisplayName?.mapToModel(),
        )
    }
}
