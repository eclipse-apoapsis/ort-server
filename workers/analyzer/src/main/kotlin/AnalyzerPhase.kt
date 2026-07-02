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

import java.io.File

import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.components.resolutions.issues.IssueResolutionService
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentForkHelper
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService

import org.jetbrains.exposed.v1.jdbc.Database

import org.ossreviewtoolkit.model.writeValue

import org.slf4j.LoggerFactory

/** The prefix of folder names created for the data exchange between different phases. */
internal const val JOB_DIR_PREFIX = "analyzer-job-"

/** The name of the file to store authentication information. */
internal const val AUTH_INFO_FILE = "auth-info.json"

/** The name of the file with data from the preparation phase. */
internal const val PREPARATION_EXCHANGE_FILE = "preparation-exchange.json"

/** The name of the directory in which package manager configuration files are created. */
internal const val CONFIG_DIR = "conf"

private val logger = LoggerFactory.getLogger(AnalyzerPhase::class.java)

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
        checkArgs(this, args, 0, 0)

        val job = ortRunService.getValidAnalyzerJob(jobId)

        return contextFactory.withContext(job.ortRunId) { context ->
            val prepareResult = worker.prepare(context, job, ortRunService, environmentService)
            val ortResult = worker.analyze(prepareResult)
            worker.processResult(context, job, ortResult, db, ortRunService, adminConfigService, issueResolutionService)
        }
    }
}

/**
 * An implementation of [AnalyzerPhase] which performs the necessary preparation steps before the ORT Analyzer can be
 * invoked. This phase is concerned with cloning the repository, creating the package manager configuration files based
 * on the environment configuration, and resolving all secrets that need to be available during the analysis.
 *
 * The [run] function expects an argument pointing to the root path of a shared directory structure under which the
 * results of this phase should be persisted. The class creates a subdirectory for the current job below this
 * structure.
 */
internal class PreparationPhase(
    /** The service to access and update information about the current run. */
    private val ortRunService: OrtRunService,

    /** The factory to create a worker context. */
    private val contextFactory: WorkerContextFactory,

    /** The service to set up the environment for the analysis. */
    private val environmentService: EnvironmentService
) : AnalyzerPhase {
    override suspend fun run(
        worker: AnalyzerWorker,
        jobId: Long,
        args: Array<String>
    ): RunResult {
        val exchangeDir = exchangeDirectory(jobId, checkArgs(this, args, 1, 1))
        val job = ortRunService.getValidAnalyzerJob(jobId)

        val prepareExchange = contextFactory.withContext(job.ortRunId) { context ->
            val configDir = exchangeDir.resolve(CONFIG_DIR).apply { mkdirs() }
            val prepareResult = worker.prepare(context, job, ortRunService, environmentService, exchangeDir, configDir)

            val authInfoFile = exchangeDir.resolve(AUTH_INFO_FILE)
            logger.info("Writing auth info file '{}'.", authInfoFile)
            EnvironmentForkHelper.persistAuthenticationInfo(authInfoFile)

            PreparationExchange(
                environment = prepareResult.resolvedEnvironment,
                runnerConfig = job.configuration.toRunnerConfig(context),
                runId = job.ortRunId
            )
        }

        writeExchangeFile(exchangeDir, PREPARATION_EXCHANGE_FILE, prepareExchange)

        return RunResult.Ignored
    }
}

/**
 * A data class to hold results of the preparation phase that needs to be exchanged with later phases.
 * [PreparationPhase] creates such an object and serializes it to a file on a shared volume. From there, it is picked
 * up to start the analysis.
 */
@Serializable
internal data class PreparationExchange(
    /** A map with environment variables that need to be set for the analysis phase. */
    val environment: Map<String, String>,

    /** The resolved configuration for the [AnalyzerRunner]. */
    val runnerConfig: AnalyzerRunnerConfig,

    /** The ID of the run that is analyzed. */
    val runId: Long
)

/**
 * Return a directory to be used for exchanging information between the different phases for the Analyzer job with the
 * given [jobId] based on the provided [args]. The root path of the volume to be used for this purpose is expected to
 * be in the first argument. Throw an exception if no valid root path is specified here.
 */
private fun exchangeDirectory(jobId: Long, args: Array<String>): File {
    require(args.isNotEmpty()) {
        "Expected an argument for the exchange directory."
    }

    val exchangeRoot = File(args[0])
    require(exchangeRoot.isDirectory) {
        "Root directory for exchange files is not a valid directory: '${exchangeRoot.absolutePath}'."
    }

    return File(exchangeRoot, "$JOB_DIR_PREFIX$jobId").also { it.mkdirs() }
}

/**
 * Check whether the number of arguments for [phase] in the given [args] is in the valid range of [minCount], and
 * [maxCount]. Throw an exception with a meaningful message if this is not the case.
 */
private fun checkArgs(phase: AnalyzerPhase, args: Array<String>, minCount: Int, maxCount: Int): Array<String> {
    require(args.size in minCount..maxCount) {
        "Unexpected arguments passed to phase '${phase.javaClass.simpleName}': '${args.joinToString(" ")}'."
    }

    return args
}

/**
 * Write a file with the given [name] and [data] in the [exchangeDir] for this [AnalyzerPhase]. Log this operation.
 */
private inline fun <reified T : Any> AnalyzerPhase.writeExchangeFile(
    exchangeDir: File,
    name: String,
    data: T
) {
    val exchangeFile = exchangeDir.resolve(name)
    logger.info("[{}]: Writing exchange file '{}'.", javaClass.simpleName, exchangeFile)

    exchangeFile.writeValue(data)
}

/**
 * Create an [AnalyzerRunnerConfig] from this [AnalyzerJobConfiguration] to be used to analyze the current project.
 * Use the given [context] to resolve the secrets in the plugin configuration.
 */
private suspend fun AnalyzerJobConfiguration.toRunnerConfig(context: WorkerContext): AnalyzerRunnerConfig =
    AnalyzerRunnerConfig(
        skipExcluded = skipExcluded,
        allowDynamicVersions = allowDynamicVersions,
        enabledPackageManagers = enabledPackageManagers,
        disabledPackageManagers = disabledPackageManagers,
        packageManagerOptions = packageManagerOptions,
        repositoryConfigPath = repositoryConfigPath,
        packageCurationProviders = context.resolveProviderPluginConfigSecrets(packageCurationProviders)
    )
