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

package org.eclipse.apoapsis.ortserver.workers.analyzer

import java.io.File

import org.eclipse.apoapsis.ortserver.components.resolutions.issues.IssueResolutionService
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.AppliedPackageCurationRef
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.ShortestDependencyPath
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToModel
import org.eclipse.apoapsis.ortserver.transport.EndpointComponent
import org.eclipse.apoapsis.ortserver.workers.common.JobIgnoredException
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService
import org.eclipse.apoapsis.ortserver.workers.common.env.config.ResolvedEnvironmentConfig
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.SecretVariableDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.SimpleVariableDefinition
import org.eclipse.apoapsis.ortserver.workers.common.resolutions.OrtServerResolutionProvider
import org.eclipse.apoapsis.ortserver.workers.common.validateForProcessing

import org.jetbrains.exposed.v1.jdbc.Database

import org.ossreviewtoolkit.model.Identifier as OrtIdentifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.utils.ort.ORT_VERSION

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AnalyzerWorker::class.java)

internal class AnalyzerWorker(
    private val downloader: AnalyzerDownloader,
    private val runner: AnalyzerRunner
) {
    /**
     * Execute an Analyzer run for the given [jobId] and [traceId] in the specified [phase] with the given [args] as
     * additional parameters. Perform a double-dispatching with the provided [phase] to run only the required steps.
     * Handle occurring exceptions and return a [RunResult] with the outcome of the operation.
     */
    suspend fun run(jobId: Long, traceId: String, phase: AnalyzerPhase, args: Array<String>): RunResult = runCatching {
        logger.info(
            "Starting {} for job {} with arguments {}.",
            phase.javaClass.simpleName,
            jobId,
            args.contentToString()
        )

        phase.run(this, jobId, args).also {
            logger.info("{} for job {} completed.", phase.javaClass.simpleName, jobId)
        }
    }.getOrElse {
        when (it) {
            is JobIgnoredException -> {
                logger.warn("Message with traceId '$traceId' ignored: ${it.message}")
                RunResult.Ignored
            }

            else -> {
                logger.error("Error while processing message with traceId '$traceId': ${it.message}")
                RunResult.Failed(it)
            }
        }
    }

    /**
     * Prepare the execution of the ORT Analyzer for the current [analyzerJob]. Use the given [context] and
     * [environmentService] to resolve required secrets and set up the environment for the analysis. Use the given
     * [ortRunService] to read and update information about the current run.
     */
    internal suspend fun prepare(
        context: WorkerContext,
        analyzerJob: AnalyzerJob,
        ortRunService: OrtRunService,
        environmentService: EnvironmentService
    ): PrepareResult {
        val ortRun = ortRunService.getOrtRun(analyzerJob.ortRunId)
            ?: throw IllegalArgumentException("The ORT run '${analyzerJob.ortRunId}' does not exist.")
        val repository = ortRunService.getHierarchyForOrtRun(ortRun.id)?.repository
            ?: throw IllegalArgumentException("The repository '${ortRun.repositoryId}' does not exist.")

        val job = ortRunService.startAnalyzerJob(analyzerJob.id)
            ?: throw IllegalArgumentException("The analyzer job with id '${analyzerJob.id}' could not be started.")
        logger.debug("Analyzer job with id '{}' started at {}.", job.id, job.startedAt)
        logger.info("Using ORT version {}.", ORT_VERSION)

        if (job.configuration.keepAliveWorker) {
            EndpointComponent.generateKeepAliveFile()
        }

        val envConfigFromJob = job.configuration.environmentConfig
        val repositoryServices =
            environmentService.findInfrastructureServicesForRepository(context, envConfigFromJob)
        if (repositoryServices.isNotEmpty()) {
            logger.info(
                "Generating a .netrc file with credentials from infrastructure services '{}' to download the " +
                        "repository.",
                repositoryServices.map(InfrastructureService::name)
            )

            environmentService.setupAuthentication(context, repositoryServices)
        }

        val downloadResult = downloader.downloadRepository(
            repository.url,
            ortRun.revision,
            ortRun.path.orEmpty(),
            job.configuration.submoduleFetchStrategy
        )

        if (downloadResult.initRevision != ortRun.revision) {
            logger.info(
                "Updating revision of ORT run from '${ortRun.revision}' to '${downloadResult.initRevision}'."
            )
            ortRunService.updateRevision(ortRun.id, downloadResult.initRevision)
        }

        ortRunService.updateResolvedRevision(ortRun.id, downloadResult.resolvedRevision)

        val resolvedEnvConfig = environmentService.setUpEnvironment(
            context,
            downloadResult.directory,
            envConfigFromJob,
            repositoryServices
        )
        val environment = resolveEnvironmentVariables(context, resolvedEnvConfig)

        return PrepareResult(downloadResult.directory, environment)
    }

    /**
     * Invoke the [AnalyzerRunner] on the prepared environment for the current [job] using the given [context] and the
     * [prepareResult] from the preparation phase. Return the [OrtResult] produced by the ORT Analyzer.
     */
    internal suspend fun analyze(context: WorkerContext, job: AnalyzerJob, prepareResult: PrepareResult): OrtResult =
        runner.run(
            context,
            prepareResult.cloneDirectory,
            job.configuration.toRunnerConfig(context),
            prepareResult.resolvedEnvironment
        )

    /**
     * Process the [ortResult] created for the current Analyzer [job]. Store it in the [database][db] with the help of
     * the given [ortRunService], [issueResolutionService], [adminConfigService], and [context]. Return a [RunResult]
     * indicating the success of the whole Analyzer run.
     */
    internal suspend fun processResult(
        context: WorkerContext,
        job: AnalyzerJob,
        ortResult: OrtResult,
        db: Database,
        ortRunService: OrtRunService,
        adminConfigService: AdminConfigService,
        issueResolutionService: IssueResolutionService
    ): RunResult {
        val ortRun = ortRunService.getValidOrtRun(job)
        ortRunService.storeRepositoryInformation(job.ortRunId, ortResult.repository)
        ortRunService.storeResolvedPackageCurations(job.ortRunId, ortResult.resolvedConfiguration.packageCurations)

        val analyzerRun = ortResult.analyzer
            ?: throw AnalyzerException("ORT Analyzer failed to create a result.")

        val excludedPackageIds = analyzerRun.result.packages
            .filter { ortResult.isPackageExcluded(it.id) }
            .mapTo(mutableSetOf()) { it.id.mapToModel() }

        val excludedProjectIds = analyzerRun.result.projects
            .filter { ortResult.isProjectExcluded(it.id) }
            .mapTo(mutableSetOf()) { it.id.mapToModel() }

            // IMPORTANT: Use getAnalyzerIssues() to get ONLY analyzer issues, not all issues.
            val allIssues = ortResult.getAnalyzerIssues().mapValues { it.value.toList() }

        val resolutionProvider = OrtServerResolutionProvider.create(
            context,
            adminConfigService,
            ortResult.repository.config.resolutions,
            RepositoryId(ortRun.repositoryId),
            issueResolutionService
        )

            // Apply resolutions using the common function.
            val resolvedItems = resolutionProvider.matchResolutions(
                issuesByIdentifier = allIssues,
                ruleViolations = emptyList(),
                vulnerabilities = emptyList()
            )

            // Calculate unresolved issues for logging.
            val unresolvedIssues = allIssues.flatMap { (ortIdentifier, issues) ->
                val identifier = ortIdentifier.takeIf { it != OrtIdentifier.EMPTY }?.mapToModel()
                issues.filter { it.mapToModel(identifier) !in resolvedItems.issues.keys }
            }

            logger.info(
                "Analyzer job ${job.id} for repository ${ortResult.repository.vcsProcessed.url} with revision " +
                        "${ortRun.revision} finished with ${allIssues.values.flatten().size} total issues " +
                        "and ${unresolvedIssues.size} unresolved issues."
            )

        val shortestPathsByIdentifier = mutableMapOf<Identifier, MutableList<ShortestDependencyPath>>()

        analyzerRun.result.projects.forEach { project ->
            getIdentifierToShortestPathsMap(
                project.id.mapToModel(),
                ortResult.dependencyNavigator.getShortestPaths(project)
            ).forEach { (identifier, path) ->
                shortestPathsByIdentifier.getOrPut(identifier) { mutableListOf() } += path
            }
        }

        db.dbQuery {
            ortRunService.getValidAnalyzerJob(job.id)
            ortRunService.storeAnalyzerRun(
                analyzerRun.mapToModel(job.id),
                shortestPathsByIdentifier,
                excludedPackageIds,
                excludedProjectIds
            )
            ortRunService.storeResolvedItems(job.ortRunId, resolvedItems)
        }

        val packageIds = ortResult.getPackages().map { it.metadata.id }.toSet()

        val packageCurationAssociations = packageIds.associate { pkgId ->
            val refs = buildList {
                ortResult.resolvedConfiguration.packageCurations.forEach { resolvedPackageCurations ->
                    resolvedPackageCurations.curations.forEachIndexed { rank, curation ->
                        if (curation.isApplicable(pkgId)) {
                            add(AppliedPackageCurationRef(resolvedPackageCurations.provider.id, rank))
                        }
                    }
                }
            }

            pkgId.mapToModel() to refs
        }.filterValues { it.isNotEmpty() }

        if (packageCurationAssociations.isNotEmpty()) {
            ortRunService.storePackageCurationAssociations(job.ortRunId, packageCurationAssociations)
        }

        return if (unresolvedIssues.any { it.severity >= Severity.WARNING }) {
            RunResult.FinishedWithIssues
        } else {
            RunResult.Success
        }
    }
}

/**
 * A data class storing the result of the preparation phase. The data contained here is needed by the [AnalyzerRunner]
 * to perform the actual analysis of the already checked out repository.
 */
internal data class PrepareResult(
    /** The directory in which the source code of the repository to be analyzed has been cloned. */
    val cloneDirectory: File,

    /** The environment variables to be passed to a forked Analyzer process if any. */
    val resolvedEnvironment: Map<String, String>
)

private class AnalyzerException(message: String) : Exception(message)

/**
 * Obtain the [AnalyzerJob] for the given [jobId] and make sure that it is valid. Throw an exception if no valid
 * job can be obtained.
 */
internal fun OrtRunService.getValidAnalyzerJob(jobId: Long) =
    getAnalyzerJob(jobId).validateForProcessing(jobId)

/**
 * Return the [OrtRun] referenced by the given [job] or throw an [IllegalArgumentException] if it does not exist.
 */
internal fun OrtRunService.getValidOrtRun(job: AnalyzerJob): OrtRun =
    getOrtRun(job.ortRunId)
        ?: throw IllegalArgumentException("The ORT run '${job.ortRunId}' does not exist.")

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

/**
 * Return a [Map] with all environment variables configured in the given [environmentConfig] with secrets resolved.
 * Use the given [context] to resolve secret values.
 */
private suspend fun resolveEnvironmentVariables(
    context: WorkerContext,
    environmentConfig: ResolvedEnvironmentConfig
): Map<String, String> {
    val allSecrets = environmentConfig.environmentVariables
        .filterIsInstance<SecretVariableDefinition>()
        .map { it.valueSecret }
    val resolvedSecrets = allSecrets.takeUnless { it.isEmpty() }?.let {
        context.resolveSecrets(*allSecrets.toTypedArray())
    }.orEmpty()
    val environment = mutableMapOf<String, String>()
    environmentConfig.environmentVariables.forEach { variable ->
        when (variable) {
            is SecretVariableDefinition -> environment[variable.name] = resolvedSecrets.getValue(variable.valueSecret)
            is SimpleVariableDefinition -> environment[variable.name] = variable.value
        }
    }

    return environment
}
