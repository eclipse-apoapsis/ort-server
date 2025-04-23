/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunFilters
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult

import org.jetbrains.exposed.sql.Database

import org.slf4j.LoggerFactory

/**
 * A service to interact with ORT runs.
 */
class OrtRunService(
    private val db: Database,
    private val ortRunRepository: OrtRunRepository,
    private val reporterJobRepository: ReporterJobRepository,
    private val reportStorageService: ReportStorageService
) {
    private val logger = LoggerFactory.getLogger(OrtRunService::class.java)

    suspend fun listOrtRuns(
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        filters: OrtRunFilters? = null
    ): ListQueryResult<OrtRun> = db.dbQuery {
        ortRunRepository.list(parameters, filters)
    }

    /**
     * Delete the ORT run with the [ortRunId] and all its reports from storage and dependent database entities.
     * In case a report does not exist in storage, although it should, the operation continues because
     * the report might have been manually deleted from storage. However, if there is a technical issue
     * during the deletion of a report from storage, the function fails and the ORT run is not deleted,
     * allowing to retry the delete operation.
     */
    suspend fun deleteOrtRun(ortRunId: Long) {
        reporterJobRepository.getForOrtRun(ortRunId)?.filenames?.forEach { filename ->
            runCatching {
                reportStorageService.deleteReport(ortRunId, filename)
            }.onFailure { e ->
                if (e is ReportNotFoundException) {
                    logger.warn("Report $filename for ORT run $ortRunId not found in storage. Continuing.")
                } else {
                    throw e
                }
            }
        }

        if (ortRunRepository.delete(ortRunId) == 0) {
            throw ResourceNotFoundException("ORT run with id '$ortRunId' not found.")
        }
    }

    /**
     * Delete all ORT runs that are older than the given [before] timestamp. Runs are deleted from the database and
     * their reports are deleted from storage.
     */
    suspend fun deleteRunsCreatedBefore(before: Instant) {
        val runIds = ortRunRepository.findRunsBefore(before)

        logger.info("Deleting ${runIds.size} ORT runs older than $before.")

        var failureCount = 0
        runIds.forEach { runId ->
            runCatching {
                deleteOrtRun(runId)
            }.onFailure { failureCount++ }
        }

        logger.info("Deleted ${runIds.size - failureCount} old ORT runs successfully.")
        if (failureCount > 0) {
            logger.warn("Failed to delete $failureCount old ORT runs.")
        }
    }
}
