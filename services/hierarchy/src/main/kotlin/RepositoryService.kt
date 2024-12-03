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

package org.eclipse.apoapsis.ortserver.services

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.dbQueryCatching
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.AdvisorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.EvaluatorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.Jobs
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunSummary
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.authorization.RepositoryRole
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.services.utils.toJoinedString

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(OrganizationService::class.java)

/**
 * A service providing functions for working with [repositories][Repository].
 */
@Suppress("LongParameterList")
class RepositoryService(
    private val db: Database,
    private val ortRunRepository: OrtRunRepository,
    private val repositoryRepository: RepositoryRepository,
    private val analyzerJobRepository: AnalyzerJobRepository,
    private val advisorJobRepository: AdvisorJobRepository,
    private val scannerJobRepository: ScannerJobRepository,
    private val evaluatorJobRepository: EvaluatorJobRepository,
    private val reporterJobRepository: ReporterJobRepository,
    private val notifierJobRepository: NotifierJobRepository,
    private val authorizationService: AuthorizationService
) {
    /**
     * Delete a repository by [repositoryId].
     */
    suspend fun deleteRepository(repositoryId: Long): Unit = db.dbQueryCatching {
        repositoryRepository.delete(repositoryId)
    }.onSuccess {
        runCatching {
            authorizationService.deleteRepositoryPermissions(repositoryId)
            authorizationService.deleteRepositoryRoles(repositoryId)
        }.onFailure { e ->
            logger.error("Error while deleting Keycloak roles for repository '$repositoryId'.", e)
        }
    }.getOrThrow()

    /**
     * Get all [Jobs] for the [OrtRun] with the provided [ortRunIndex] and [repositoryId].
     */
    suspend fun getJobs(repositoryId: Long, ortRunIndex: Long): Jobs? = db.dbQuery {
        val ortRun = ortRunRepository.getByIndex(repositoryId, ortRunIndex) ?: return@dbQuery null

        val analyzerJob = analyzerJobRepository.getForOrtRun(ortRun.id)
        val advisorJob = advisorJobRepository.getForOrtRun(ortRun.id)
        val scannerJob = scannerJobRepository.getForOrtRun(ortRun.id)
        val evaluatorJob = evaluatorJobRepository.getForOrtRun(ortRun.id)
        val reporterJob = reporterJobRepository.getForOrtRun(ortRun.id)
        val notifierJob = notifierJobRepository.getForOrtRun(ortRun.id)
        Jobs(analyzerJob, advisorJob, scannerJob, evaluatorJob, reporterJob, notifierJob)
    }

    suspend fun getOrtRun(repositoryId: Long, ortRunIndex: Long): OrtRun? = db.dbQuery {
        ortRunRepository.getByIndex(repositoryId, ortRunIndex)
    }

    /**
     * Get the id of an ORT run by its [index][ortRunIndex] within a [repository][repositoryId].
     * This function is more efficient than [getOrtRun], as it only retrieves the ID of the ORT run or
     * returns null if the ORT run is not found.
     */
    suspend fun getOrtRunId(repositoryId: Long, ortRunIndex: Long): Long? = db.dbQuery {
        ortRunRepository.getIdByIndex(repositoryId, ortRunIndex)
    }

    /**
     * Get the summaries of the runs executed on the given [repository][repositoryId] according
     * to the given [parameters].
     */
    suspend fun getOrtRunSummaries(
        repositoryId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): ListQueryResult<OrtRunSummary> = db.dbQuery {
        ortRunRepository.listSummariesForRepository(repositoryId, parameters)
    }

    /**
     * Get a repository by [repositoryId]. Returns null if the repository is not found.
     */
    suspend fun getRepository(repositoryId: Long): Repository? = db.dbQuery {
        repositoryRepository.get(repositoryId)
    }

    /**
     * Update a repository by [repositoryId] with the [present][OptionalValue.Present] values.
     */
    suspend fun updateRepository(
        repositoryId: Long,
        type: OptionalValue<RepositoryType> = OptionalValue.Absent,
        url: OptionalValue<String> = OptionalValue.Absent
    ): Repository = db.dbQuery {
        repositoryRepository.update(repositoryId, type, url)
    }

    /**
     * Remove a user from a group that grant roles and permissions on repository level.
     */
    suspend fun removeUserFromGroup(
        username: String,
        repositoryId: Long,
        groupId: String
    ) {
        getRepository(repositoryId)
            ?: throw ResourceNotFoundException("Repository with repositoryId '$repositoryId' not found.")

        val repositoryRole = try {
            RepositoryRole.valueOf(groupId.uppercase().removeSuffix("S"))
        } catch (e: IllegalArgumentException) {
            throw ResourceNotFoundException(
                "Group with groupId '$groupId' not found. Must be one of ${RepositoryRole.entries.toJoinedString()}",
                e
            )
        }

        val groupName = repositoryRole.groupName(repositoryId)

        // As the AuthorizationService does not distinguish between technical exceptions (e.g. cannot connect to
        // Keycloak) and business exceptions (e.g. user not found), we can't do special exception handling here
        // and just let the exception propagate.
        authorizationService.removeUserFromGroup(username, groupName)
    }

    /**
     * Add a user to one of the three groups that grant roles and permissions on repository level.
     */
    suspend fun addUserToGroup(
        username: String,
        repositoryId: Long,
        groupId: String
    ) {
        getRepository(repositoryId)
            ?: throw ResourceNotFoundException("Repository with repositoryId '$repositoryId' not found.")

        val repositoryRole = try {
            RepositoryRole.valueOf(groupId.uppercase().removeSuffix("S"))
        } catch (e: IllegalArgumentException) {
            throw ResourceNotFoundException(
                "Group with groupId '$groupId' not found. Must be one of ${RepositoryRole.entries.toJoinedString()}",
                e
            )
        }

        val groupName = repositoryRole.groupName(repositoryId)

        // As the AuthorizationService does not distinguish between technical exceptions (e.g. cannot connect to
        // Keycloak) and business exceptions (e.g. user not found), we can't do special exception handling here
        // and just let the exception propagate.
        authorizationService.addUserToGroup(username, groupName)
    }

    /**
     * Get the ID of the latest ORT run of the repository where the analyzer job is in a final state.
     */
    suspend fun getLatestOrtRunIdWithAnalyzerJobInFinalState(repositoryId: Long): Long? = db.dbQuery {
        AnalyzerJobsTable
            .innerJoin(OrtRunsTable)
            .select(AnalyzerJobsTable.ortRunId)
            .where {
                (OrtRunsTable.repositoryId eq repositoryId) and
                        (AnalyzerJobsTable.status inList JobStatus.FINAL_STATUSES)
            }
            .orderBy(OrtRunsTable.index, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.get(AnalyzerJobsTable.ortRunId)
            ?.value
    }

    /**
     * Get the ID of the latest ORT run of the repository where the analyzer job has succeeded.
     */
    suspend fun getLatestOrtRunIdWithSuccessfulAnalyzerJob(repositoryId: Long): Long? = db.dbQuery {
        AnalyzerJobsTable
            .innerJoin(OrtRunsTable)
            .select(AnalyzerJobsTable.ortRunId)
            .where {
                (OrtRunsTable.repositoryId eq repositoryId) and
                        (AnalyzerJobsTable.status inList JobStatus.SUCCESSFUL_STATUSES)
            }
            .orderBy(OrtRunsTable.index, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.get(AnalyzerJobsTable.ortRunId)
            ?.value
    }

    /**
     * Get the ID of the latest ORT run of the repository where the advisor job has succeeded.
     */
    suspend fun getLatestOrtRunIdWithSuccessfulAdvisorJob(repositoryId: Long): Long? = db.dbQuery {
        AdvisorJobsTable
            .innerJoin(OrtRunsTable)
            .select(AdvisorJobsTable.ortRunId)
            .where {
                (OrtRunsTable.repositoryId eq repositoryId) and
                        (AdvisorJobsTable.status inList JobStatus.SUCCESSFUL_STATUSES)
            }
            .orderBy(OrtRunsTable.index, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.get(AdvisorJobsTable.ortRunId)
            ?.value
    }

    /**
     * Get the ID of the latest ORT run of the repository where the evaluator job has succeeded.
     */
    suspend fun getLatestOrtRunIdWithSuccessfulEvaluatorJob(repositoryId: Long): Long? = db.dbQuery {
        EvaluatorJobsTable
            .innerJoin(OrtRunsTable)
            .select(EvaluatorJobsTable.ortRunId)
            .where {
                (OrtRunsTable.repositoryId eq repositoryId) and
                        (EvaluatorJobsTable.status inList JobStatus.SUCCESSFUL_STATUSES)
            }
            .orderBy(OrtRunsTable.index, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.get(EvaluatorJobsTable.ortRunId)
            ?.value
    }
}
