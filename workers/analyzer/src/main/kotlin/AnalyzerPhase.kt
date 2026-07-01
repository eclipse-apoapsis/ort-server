/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.analyzer

import org.eclipse.apoapsis.ortserver.components.resolutions.issues.IssueResolutionService
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService

import org.jetbrains.exposed.v1.jdbc.Database

/**
 * An interface representing a phase of the Analyzer execution.
 *
 * The Analyzer can run in different phases with different configurations to reduce the impact of untrusted code it
 * might be exposed to when analyzing build scripts. For instance, a preparation phase to set up the build environment
 * needs access to the secret storage to generate the package manager configuration files according to the environment
 * definition. In the actual analysis phase, this is no longer required.
 *
 * The idea behind this interface is that each implementation corresponds to a specific phase of an Analyzer run. The
 * whole functionality of the run is implemented by [AnalyzerWorker]. A concrete phase implementation invokes the
 * functions of the worker to execute the corresponding steps. It has access to the dependencies and services it needs
 * for its specific task - and only to those. This allows for a clear separation between the single phases.
 *
 * The Analyzer endpoint determines the phase to be used based on command line arguments. It inspects the passed in
 * command line and obtains the referenced [AnalyzerPhase] implementation. The command line may contain additional
 * arguments to be interpreted by the current phase.
 */
internal sealed interface AnalyzerPhase {
    /**
     * Execute this phase for the Analyzer job with the given [jobId] by invoking the given [worker].
     * Obtain relevant parameters from the given [command line arguments][args]. Return a [RunResult] the indicates to
     * the Analyzer endpoint how to handle the result.
     */
    suspend fun run(worker: AnalyzerWorker, jobId: Long, args: Array<String>): RunResult
}

/**
 * An implementation of [AnalyzerPhase] that executes the whole Analyzer run in a single shot. This is the most
 * efficient way to analyze a repository, since there is no need to temporary store and exchange intermediate results.
 * There is, however, no isolation between the single steps; the full infrastructure is accessible during the whole
 * analysis.
 *
 * This implementation does not support any command line arguments.
 */
internal class FullPhase(
    /** The database to access the job and store the results. */
    private val db: Database,

    /** The service to access and update information about the current run. */
    private val ortRunService: OrtRunService,

    /** The factory to create a worker context. */
    private val contextFactory: WorkerContextFactory,

    /** The service to set up the environment for the analysis. */
    private val environmentService: EnvironmentService,

    /** The service to access the global server configuration. */
    private val adminConfigService: AdminConfigService,

    /** The service to handle issues. */
    private val issueResolutionService: IssueResolutionService
) : AnalyzerPhase {
    override suspend fun run(worker: AnalyzerWorker, jobId: Long, args: Array<String>): RunResult {
        require(args.isEmpty()) {
            "Unexpected arguments passed to the 'FULL' phase: '${args.joinToString(" ")}'."
        }

        val job = ortRunService.getValidAnalyzerJob(jobId)

        return contextFactory.withContext(job.ortRunId) { context ->
            val prepareResult = worker.prepare(context, job, ortRunService, environmentService)
            val ortResult = worker.analyze(context, job, prepareResult)
            worker.processResult(context, job, ortResult, db, ortRunService, adminConfigService, issueResolutionService)
        }
    }
}
