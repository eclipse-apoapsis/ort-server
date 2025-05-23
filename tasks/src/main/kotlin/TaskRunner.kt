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

import kotlin.system.exitProcess

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

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
import org.eclipse.apoapsis.ortserver.services.ReportStorageService
import org.eclipse.apoapsis.ortserver.services.ortrun.OrphanRemovalService
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtServerFileListStorage
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.tasks.impl.DeleteOldOrtRunsTask
import org.eclipse.apoapsis.ortserver.tasks.impl.DeleteOrphanedEntitiesTask
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

/** The name of the environment variable that determines whether the JVM should be kept alive after task execution. */
private const val KEEP_JVM_VARIABLE = "TASKS_KEEP_JVM"

/**
 * The main entry point of the task runner component.
 *
 * This function triggers the execution of the tasks whose names are specified in the configuration. The configuration
 * is read from a section named `taskRunner`. It supports the following properties:
 * - `tasks`: A comma-separated list of names for the tasks to be executed. This can be overridden using the `TASKS`
 *   environment variable.
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
 * - `kubernetes-reaper`: Cleans up completed Kubernetes jobs and also notifies the Orchestrator about failed jobs.
 *   This task is part of the Kubernetes Job Monitor.
 * - `kubernetes-lost-jobs-finder`: Checks for jobs that are active, according to the database, but for which no
 *   running Kubernetes job exists. Such a constellation indicates a fatal crash of a worker job. This task is part of
 *   the Kubernetes Job Monitor.
 * - `kubernetes-long-running-jobs-finder`: Checks for worker jobs in Kubernetes that are running longer than a
 *   configured timeout and terminates them. This task is part of the Kubernetes Job Monitor.
 */
suspend fun main() {
    runTasks(listOf(configModule(), databaseModule(), tasksModule()))

    endTaskRunner()
}

/**
 * Set up a Koin application with the given [modules] and run all configured tasks. Note that a module for the
 * configuration is added automatically.
 *
 * The tasks to run are defined via the configuration as a comma-separated list of task names. To change the set of
 * tasks dynamically, the configuration can be overwritten using an environment variable. The task names must match
 * named bean definitions of type [Task] in the Koin modules. The function fetches all referenced tasks from Koin and
 * executes them asynchronously.
 */
internal suspend fun runTasks(modules: List<Module>) {
    logger.info("Setting up Koin application.")
    val app = startKoin {
        modules(modules)
    }

    try {
        val config = app.koin.get<ConfigManager>().subConfig(Path(TASK_RUNNER_SECTION))
        val tasksToRun = config.getString(TASKS_PROPERTY).split(',')

        withContext(Dispatchers.IO) {
            tasksToRun.map { taskName ->
                async {
                    logger.info("Executing task '$taskName'.")
                    runCatching {
                        val task = app.koin.get<Task>(named(taskName))
                        task.execute()
                        logger.info("Task '$taskName' executed successfully.")
                    }.onFailure { e ->
                        logTaskExecutionError(taskName, e)
                    }
                }
            }.awaitAll()
        }
    } finally {
        stopKoin()
    }
}

/**
 * Exit the JVM depending on the value of the corresponding environment variable. This function is called after task
 * execution to make sure that the JVM process is cleanly terminated.
 */
internal fun endTaskRunner() {
    if (!System.getenv(KEEP_JVM_VARIABLE).toBoolean()) {
        logger.info("Exiting JVM after task execution.")
        exitProcess(0)
    }
}

/**
 * Create a [Module] that provides access to the configuration of the tasks.
 */
internal fun configModule(): Module =
    module {
        single { ConfigManager.create(ConfigFactory.load()) }
    }

/**
 * Create a [Module] for the standard tasks and their dependencies.
 */
private fun tasksModule(): Module =
    module {
        single {
            val storage = Storage.create("reportStorage", get())
            ReportStorageService(storage, get())
        }

        single {
            val storage = Storage.create(OrtServerFileListStorage.STORAGE_TYPE, get())
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
        singleOf(::OrtRunService)

        singleOf(DeleteOldOrtRunsTask::create) {
            named("delete-old-ort-runs")
            bind<Task>()
        }
        singleOf(DeleteOrphanedEntitiesTask::create) {
            named("delete-orphaned-entities")
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
    }

/**
 * Log an [exception] about a failed execution of the task with the given [taskName]. Based on the concrete exception,
 *
 */
private fun logTaskExecutionError(taskName: String, exception: Throwable) {
    when (exception) {
        is NoDefinitionFoundException -> logger.error("Task '$taskName' does not exist.", exception)
        else -> logger.error("Execution of task '$taskName' failed.", exception)
    }
}
