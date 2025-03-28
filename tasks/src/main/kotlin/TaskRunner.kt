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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.dao.databaseModule
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.DaoOrtRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob.DaoReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.services.OrphanRemovalService
import org.eclipse.apoapsis.ortserver.services.OrtRunService
import org.eclipse.apoapsis.ortserver.services.ReportStorageService
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.tasks.impl.DeleteOldOrtRunsTask
import org.eclipse.apoapsis.ortserver.tasks.impl.DeleteOrphanedEntitiesTask

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.error.NoDefinitionFoundException
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("TaskRunner")

/**
 * The main entry point of the task runner component.
 *
 * This function triggers the execution of the tasks whose names are specified in the configuration. The configuration
 * is read from a section named `taskRunner`. It supports the following properties:
 * - `tasks`: A comma-separated list of names for the tasks to be executed. This can be overridden using the `TASKS`
 *   environment variable.
 *
 * The following tasks are available:
 * - `delete-old-ort-runs`: Deletes old ORT runs according to the configured data retention policy.
 */
suspend fun main() {
    runTasks(listOf(configModule(), databaseModule(), tasksModule()))
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
        val config = app.koin.get<ConfigManager>().subConfig(Path("taskRunner"))
        val tasksToRun = config.getString("tasks").split(',')

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
        single<OrtRunRepository> { DaoOrtRunRepository(get()) }
        single<ReporterJobRepository> { DaoReporterJobRepository(get()) }

        single { Storage.create("reportStorage", get()) }

        singleOf(::OrtRunService)
        singleOf(::ReportStorageService)
        singleOf(::OrphanRemovalService)

        single<Task>(named("delete-old-ort-runs")) { DeleteOldOrtRunsTask.create(get(), get()) }
        single<Task>(named("delete-orphaned-entities")) { DeleteOrphanedEntitiesTask.create(get(), get()) }
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
