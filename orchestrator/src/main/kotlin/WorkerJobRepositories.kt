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

package org.eclipse.apoapsis.ortserver.orchestrator

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.WorkerJobRepository
import org.eclipse.apoapsis.ortserver.model.util.asPresent
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EvaluatorEndpoint
import org.eclipse.apoapsis.ortserver.transport.NotifierEndpoint
import org.eclipse.apoapsis.ortserver.transport.ReporterEndpoint
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint

/**
 * A data class that holds references to the repositories of the worker jobs. This is used to have all these
 * repositories in one place, rather than passing around many references.
 */
class WorkerJobRepositories(
    /** The repository for Analyzer jobs. */
    val analyzerJobRepository: AnalyzerJobRepository,

    /** The repository for Advisor jobs. */
    val advisorJobRepository: AdvisorJobRepository,

    /** The repository for Scanner jobs. */
    val scannerJobRepository: ScannerJobRepository,

    /** The repository for Evaluator jobs. */
    val evaluatorJobRepository: EvaluatorJobRepository,

    /** The repository for Reporter jobs. */
    val reporterJobRepository: ReporterJobRepository,

    /** The repository for Notifier jobs. */
    val notifierJobRepository: NotifierJobRepository
) {
    /** A map allowing direct access to a repository for a specific worker endpoint. */
    val jobRepositories = mapOf(
        AnalyzerEndpoint.configPrefix to analyzerJobRepository,
        AdvisorEndpoint.configPrefix to advisorJobRepository,
        ScannerEndpoint.configPrefix to scannerJobRepository,
        EvaluatorEndpoint.configPrefix to evaluatorJobRepository,
        ReporterEndpoint.configPrefix to reporterJobRepository,
        NotifierEndpoint.configPrefix to notifierJobRepository
    )

    /**
     * Return the [WorkerJobRepository] for the given [endpoint] or *null* if there is no such repository.
     */
    operator fun get(endpoint: Endpoint<*>): WorkerJobRepository<*>? = get(endpoint.configPrefix)

    /**
     * Return the [WorkerJobRepository] for the endpoint with the given [endpointName] or *null* if there is no such
     * repository.
     */
    operator fun get(endpointName: String): WorkerJobRepository<*>? = jobRepositories[endpointName]

    /**
     * Update the status for a job for the given [endpoint] and [jobId] in the database to the provided [status].
     * If [finished] is *true*, also set the finished time.
     */
    fun updateJobStatus(
        endpoint: Endpoint<*>,
        jobId: Long,
        status: JobStatus,
        finished: Boolean = true,
        errorMessage: String? = null
    ): WorkerJob {
        val repository = jobRepositories.getValue(endpoint.configPrefix)

        return requireNotNull(repository.get(jobId)) {
            "Job for endpoint '${endpoint.configPrefix}' with ID '$jobId' not found."
        }.also {
            if (finished) {
                repository.complete(jobId, Clock.System.now(), status, errorMessage)
            } else {
                repository.update(
                    id = jobId,
                    status = status.asPresent()
                )
            }
        }
    }
}
