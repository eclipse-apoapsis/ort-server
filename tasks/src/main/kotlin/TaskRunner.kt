/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.tasks

import com.typesafe.config.ConfigFactory

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.util.ClientBuilder

import java.io.File
import java.io.IOException

import kotlin.system.exitProcess

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.components.projections.ProjectionRegistry
import org.eclipse.apoapsis.ortserver.components.reportstorage.reportStorageModule
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.DaoAdvisorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.DaoAdvisorRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.DaoAnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.DaoAnalyzerRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.DaoEvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.DaoEvaluatorRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.notifierjob.DaoNotifierJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.notifierrun.DaoNotifierRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.DaoOrtRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob.DaoReporterJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterrun.DaoReporterRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.DaoRepositoryRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.DaoRepositoryConfigurationRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.DaoResolvedConfigurationRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.DaoScannerJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.DaoScannerRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryConfigurationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ResolvedConfigurationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerRunRepository
import org.eclipse.apoapsis.ortserver.services.ortrun.OrphanRemovalService
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtServerFileListStorage
import org.eclipse.apoapsis.ortserver.shared.coroutines.Virtual
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.tasks.impl.DeleteOldOrtRunsTask
import org.eclipse.apoapsis.ortserver.tasks.impl.DeleteOrphanedEntitiesTask
import org.eclipse.apoapsis.ortserver.tasks.impl.DeleteUnusedProjectionsTask
import org.eclipse.apoapsis.ortserver.tasks.impl.SyncKeycloakUsersTask
import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.FailedJobNotifier
import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.JobHandler
import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.LongRunningJobsFinderTask
import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.LostJobsFinderTask
import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.MonitorConfig
import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.ReaperTask
import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.TimeHelper
import org.eclipse.apoapsis.ortserver.transport.MessageSenderFactory
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.error.NoDefinitionFoundException
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.named
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

import org.ossreviewtoolkit.downloader.DefaultWorkingTreeCache
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.scanner.provenance.DefaultProvenanceDownloader
import org.ossreviewtoolkit.scanner.utils.FileListResolver

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("TaskRunner")

/** The name of the configuration section with properties for the task runner. */
private const val TASK_RUNNER_SECTION = "taskRunner"

/** The name of the configuration property that defines the tasks to be executed. */
private const val TASKS_PROPERTY = "tasks"

/** Line breaks, together with the whitespace around them, to be replaced in the reason of a failed task. */
private val LINE_BREAKS = Regex("\\s*\\R\\s*")

/** The name under which a failure to set up the task runner itself is reported. */
private const val TASK_RUNNER_NAME = "task runner"

/** The name of the environment variable that determines whether the JVM should be kept alive after task execution. */
private const val KEEP_JVM_VARIABLE = "TASKS_KEEP_JVM"

/** The name of the environment variable that overrides the path of the termination message file. */
private const val TERMINATION_MESSAGE_PATH_VARIABLE = "TASKS_TERMINATION_MESSAGE_PATH"

/** The file that Kubernetes reads as the termination message of a container, unless configured otherwise. */
private const val DEFAULT_TERMINATION_MESSAGE_PATH = "/dev/termination-log"

/**
 * The main entry point of the task runner component.
 *
 * This function triggers the execution of the tasks whose names are specified in the configuration. The configuration
 * is read from a section named `taskRunner`. It supports the following properties:
 * - `tasks`: A comma-separated list of names for the tasks to be executed. This can be overridden using the `TASKS`
 *   environment variable.
 *
 * The process exits with code 1 if any task fails, if a task does not exist, or if the task runner cannot be set up,
 * and with code 0 otherwise. The failed tasks and their reasons are also written to the termination message file of
 * Kubernetes, `/dev/termination-log`, if that file exists. The `TASKS_TERMINATION_MESSAGE_PATH` environment variable
 * changes its path.
 *
 * In addition, the function reads the `TASKS_KEEP_JVM` environment variable. Its value is a flag that controls whether
 * the task runner should end the JVM process after executing the tasks. This may be required to prevent that some
 * background threads keep the JVM alive. Therefore, the default value of this property is `false`. It is mainly
 * overridden in tests, since the JVM should not be shut down by unit tests.
 *
 * The following tasks are available:
 * - `delete-old-ort-runs`: Deletes old ORT runs according to the configured data retention policy.
 * - `delete-orphaned-entities`: Deletes entities from the database that are shared between ORT runs, but are no longer
 *   referenced by any run.
 * - `delete-unused-projections`: Deletes projections that have not been accessed for the configured number of days.
 * - `kubernetes-reaper`: Cleans up completed Kubernetes jobs and also notifies the Orchestrator about failed jobs.
 *   This task is part of the Kubernetes Job Monitor.
 * - `kubernetes-lost-jobs-finder`: Checks for jobs that are active, according to the database, but for which no
 *   running Kubernetes job exists. Such a constellation indicates a fatal crash of a worker job. This task is part of
 *   the Kubernetes Job Monitor.
 * - `kubernetes-long-running-jobs-finder`: Checks for worker jobs in Kubernetes that are running longer than a
 *   configured timeout and terminates them. This task is part of the Kubernetes Job Monitor.
 * - `sync-keycloak-users`: Syncs the stored users and the role assignments with the users in Keycloak.
 */
suspend fun main() {
    val exitCode = runTaskRunner(listOf(configModule(), databaseModule(), tasksModule()))

    if (!System.getenv(KEEP_JVM_VARIABLE).toBoolean()) exitProcess(exitCode)
}

/** A task that failed, with a one-line [reason] for the operator. */
internal data class TaskFailure(val taskName: String, val reason: String)

/**
 * Run the configured tasks with the given [modules] and log a summary of the tasks that failed. Return the exit code
 * for the process: 1 if a task failed or the task runner could not be set up, 0 otherwise.
 */
internal suspend fun runTaskRunner(modules: List<Module>): Int {
    val failures = runCatching { runTasks(modules) }.getOrElse { e ->
        logger.error("Could not set up the task runner.", e)
        listOf(TaskFailure(TASK_RUNNER_NAME, e.toReason()))
    }

    if (failures.isEmpty()) return 0

    // Write the termination message first, so that the summary stays the last line of the output.
    writeTerminationMessage(failures)
    logger.error("Failed tasks: ${failures.joinToString { "${it.taskName} (${it.reason})" }}.")
    return 1
}

/**
 * Write the given [failures] to the file that Kubernetes shows as the termination message of the container, so that
 * operators can see why tasks failed without reading the logs. Do nothing if the file does not exist, which is the
 * case outside Kubernetes.
 */
private fun writeTerminationMessage(failures: List<TaskFailure>) {
    val file = File(System.getenv(TERMINATION_MESSAGE_PATH_VARIABLE) ?: DEFAULT_TERMINATION_MESSAGE_PATH)
    if (!file.exists()) return

    // Kubernetes truncates the termination message to the size it allows for the container.
    try {
        file.writeText(failures.joinToString("\n") { "${it.taskName}: ${it.reason}" })
    } catch (e: IOException) {
        logger.warn("Could not write the termination message to '$file'.", e)
    }
}

/**
 * Set up a Koin application with the given [modules] and run all configured tasks.
 *
 * The tasks to run are defined via the configuration as a comma-separated list of task names. To change the set of
 * tasks dynamically, the configuration can be overwritten using an environment variable. The task names must match
 * named bean definitions of type [Task] in the Koin modules. The function fetches all referenced tasks from Koin and
 * executes them asynchronously. Return the tasks that failed.
 */
internal suspend fun runTasks(modules: List<Module>): List<TaskFailure> {
    logger.info("Setting up Koin application.")
    val app = startKoin {
        modules(modules)
    }

    return try {
        val config = app.koin.get<ConfigManager>().subConfig(Path(TASK_RUNNER_SECTION))
        val tasksToRun = config.getString(TASKS_PROPERTY).split(',')

        withContext(Dispatchers.Virtual) {
            tasksToRun.map { taskName ->
                async {
                    logger.info("Executing task '$taskName'.")
                    runCatching {
                        val task = app.koin.get<Task>(named(taskName))
                        task.execute()
                        logger.info("Task '$taskName' executed successfully.")
                    }.exceptionOrNull()?.let { e ->
                        TaskFailure(taskName, logTaskExecutionError(taskName, e))
                    }
                }
            }.awaitAll().filterNotNull()
        }
    } finally {
        stopKoin()
    }
}

/** Create a [Module] that provides access to the configuration of the tasks. */
internal fun configModule(): Module =
    module {
        single { ConfigManager.create(ConfigFactory.load()) }
    }

/** Create a [Module] for the standard tasks and their dependencies. */
private fun tasksModule(): Module =
    module {
        includes(reportStorageModule)

        single {
            val storage = Storage.create(OrtServerFileListStorage.STORAGE_TYPE, get(), get())
            FileListResolver(
                OrtServerFileListStorage(storage),
                DefaultProvenanceDownloader(DownloaderConfiguration(), DefaultWorkingTreeCache())
            )
        }

        singleOf(ClientBuilder::defaultClient)
        single { BatchV1Api(get()) }
        single { CoreV1Api(get()) }

        single { MessageSenderFactory.createSender(OrchestratorEndpoint, get()) }

        single<AdvisorJobRepository> { DaoAdvisorJobRepository(get()) }
        single<AdvisorRunRepository> { DaoAdvisorRunRepository(get()) }
        single<AnalyzerJobRepository> { DaoAnalyzerJobRepository(get()) }
        single<AnalyzerRunRepository> { DaoAnalyzerRunRepository(get()) }
        single<EvaluatorJobRepository> { DaoEvaluatorJobRepository(get()) }
        single<EvaluatorRunRepository> { DaoEvaluatorRunRepository(get()) }
        single<NotifierJobRepository> { DaoNotifierJobRepository(get()) }
        single<NotifierRunRepository> { DaoNotifierRunRepository(get()) }
        single<OrtRunRepository> { DaoOrtRunRepository(get()) }
        single<ReporterJobRepository> { DaoReporterJobRepository(get()) }
        single<RepositoryRepository> { DaoRepositoryRepository(get()) }
        single<ReporterRunRepository> { DaoReporterRunRepository(get()) }
        single<RepositoryConfigurationRepository> { DaoRepositoryConfigurationRepository(get()) }
        single<ResolvedConfigurationRepository> { DaoResolvedConfigurationRepository(get()) }
        single<ScannerJobRepository> { DaoScannerJobRepository(get()) }
        single<ScannerRunRepository> { DaoScannerRunRepository(get()) }

        singleOf(::FailedJobNotifier)
        singleOf(::JobHandler)
        singleOf(MonitorConfig::create)
        single { TimeHelper() }

        singleOf(::OrphanRemovalService)
        singleOf(::ProjectionRegistry)
        singleOf(::OrtRunService)

        singleOf(DeleteOldOrtRunsTask::create) {
            named("delete-old-ort-runs")
            bind<Task>()
        }
        singleOf(DeleteOrphanedEntitiesTask::create) {
            named("delete-orphaned-entities")
            bind<Task>()
        }
        singleOf(DeleteUnusedProjectionsTask::create) {
            named("delete-unused-projections")
            bind<Task>()
        }
        singleOf(::LongRunningJobsFinderTask) {
            named("kubernetes-long-running-jobs-finder")
            bind<Task>()
        }
        singleOf(::LostJobsFinderTask) {
            named("kubernetes-lost-jobs-finder")
            bind<Task>()
        }
        singleOf(::ReaperTask) {
            named("kubernetes-reaper")
            bind<Task>()
        }
        singleOf(SyncKeycloakUsersTask::create) {
            named("sync-keycloak-users")
            bind<Task>()
        }
    }

/**
 * Log an [exception] about a failed execution of the task with the given [taskName], and return the reason of the
 * failure for the operator.
 */
private fun logTaskExecutionError(taskName: String, exception: Throwable): String =
    when (exception) {
        is NoDefinitionFoundException -> {
            logger.error("Task '$taskName' does not exist.", exception)
            "Task '$taskName' does not exist."
        }

        else -> {
            logger.error("Execution of task '$taskName' failed.", exception)
            exception.toReason()
        }
    }

/**
 * Return a one-line description of this exception with its class name and message. Line breaks in the message are
 * replaced by spaces, so that the summary of failed tasks stays a single line.
 */
private fun Throwable.toReason(): String = "${this::class.simpleName}: ${message?.replace(LINE_BREAKS, " ")}"
