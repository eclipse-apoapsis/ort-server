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
import org.eclipse.apoapsis.ortserver.model.Jobs
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

import org.jetbrains.exposed.sql.Database

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(OrganizationService::class.java)

/**
 * A service providing functions for working with [repositories][Repository].
 */
class RepositoryService(
    private val db: Database,
    private val ortRunRepository: OrtRunRepository,
    private val repositoryRepository: RepositoryRepository,
    private val analyzerJobRepository: AnalyzerJobRepository,
    private val advisorJobRepository: AdvisorJobRepository,
    private val scannerJobRepository: ScannerJobRepository,
    private val evaluatorJobRepository: EvaluatorJobRepository,
    private val reporterJobRepository: ReporterJobRepository,
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
        }.onFailure {
            logger.error("Error while deleting Keycloak roles for repository '$repositoryId'.", it)
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

        Jobs(analyzerJob, advisorJob, scannerJob, evaluatorJob, reporterJob)
    }

    suspend fun getOrtRun(repositoryId: Long, ortRunIndex: Long): OrtRun? = db.dbQuery {
        ortRunRepository.getByIndex(repositoryId, ortRunIndex)
    }

    /**
     * Get the runs executed on the given [repository][repositoryId] according to the given [parameters].
     */
    suspend fun getOrtRuns(
        repositoryId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): List<OrtRun> = db.dbQuery {
        ortRunRepository.listForRepository(repositoryId, parameters)
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
}
