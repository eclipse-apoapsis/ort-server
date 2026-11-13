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

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.file.exist
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.types.beInstanceOf

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import org.eclipse.apoapsis.ortserver.dao.test.mockDatabaseModule
import org.eclipse.apoapsis.ortserver.dao.test.unmockDatabaseModule
import org.eclipse.apoapsis.ortserver.dao.test.verifyDatabaseModuleIncluded
import org.eclipse.apoapsis.ortserver.tasks.impl.DeleteOldOrtRunsTask
import org.eclipse.apoapsis.ortserver.tasks.impl.DeleteOrphanedEntitiesTask
import org.eclipse.apoapsis.ortserver.tasks.impl.DeleteUnusedProjectionsTask
import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.LongRunningJobsFinderTask
import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.LostJobsFinderTask
import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.ReaperTask
import org.eclipse.apoapsis.ortserver.transport.testing.TEST_TRANSPORT_NAME

import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest

class TaskRunnerTest : KoinTest, WordSpec() {
    init {
        beforeTest {
            mockDatabaseModule()
        }

        afterTest {
            unmockDatabaseModule()
        }

        "runTaskRunner" should {
            "execute a single task" {
                val (taskLog, exitCode) = checkTaskExecution("task1")

                taskLog.getExecutedTasks() should containExactly("task1")
                exitCode shouldBe 0
            }

            "execute multiple tasks" {
                val (taskLog, exitCode) = checkTaskExecution("task1", "task2")

                taskLog.getExecutedTasks() should containExactlyInAnyOrder("task1", "task2")
                exitCode shouldBe 0
            }

            "handle errors during task execution" {
                val (taskLog, exitCode) = checkTaskExecution("task1", ERROR_TASK, "task2")

                taskLog.getExecutedTasks() should containExactlyInAnyOrder("task1", ERROR_TASK, "task2")
                exitCode shouldBe 1
            }

            "handle a non-existing task" {
                val (taskLog, exitCode) = checkTaskExecution("task1", "nonExistingTask", "task2")

                taskLog.getExecutedTasks() should containExactlyInAnyOrder("task1", "task2")
                exitCode shouldBe 1
            }

            "fail if the task runner cannot be set up" {
                val exitCode = withEnvironment(mapOf("TASKS" to null)) {
                    ConfigFactory.invalidateCaches()
                    runTaskRunner(listOf(configModule(), createTestModule(TaskLog())))
                }

                exitCode shouldBe 1
            }
        }

        "the termination message" should {
            "name the failed tasks and their reasons" {
                val file = tempfile()

                val (_, exitCode) = withTerminationMessagePath(file.path) {
                    checkTaskExecution("task1", ERROR_TASK)
                }

                exitCode shouldBe 1
                file.readText() shouldBe "$ERROR_TASK: IOException: Test exception: Task execution failed."
            }

            "not be written if all tasks succeed" {
                val file = tempfile()

                withTerminationMessagePath(file.path) { checkTaskExecution("task1") }

                file.readText() should beEmpty()
            }

            "not be created if the file does not exist" {
                val file = tempdir().resolve("termination-log")

                withTerminationMessagePath(file.path) { checkTaskExecution(ERROR_TASK) }

                file shouldNot exist()
            }

            "not change the exit code if it cannot be written" {
                val directory = tempdir()

                val (_, exitCode) = withTerminationMessagePath(directory.path) { checkTaskExecution(ERROR_TASK) }

                exitCode shouldBe 1
            }
        }

        "runTasks" should {
            "return the reason of a failed task" {
                val failures = runTestTasks(TaskLog(), "task1", ERROR_TASK)

                failures should containExactly(
                    TaskFailure(ERROR_TASK, "IOException: Test exception: Task execution failed.")
                )
            }

            "return the reason of a failed task on a single line" {
                val failures = runTestTasks(TaskLog(), MULTILINE_ERROR_TASK)

                failures should containExactly(
                    TaskFailure(MULTILINE_ERROR_TASK, "IllegalStateException: First line. Second line.")
                )
            }

            "return a failure for a task that does not exist" {
                val failures = runTestTasks(TaskLog(), "nonExistingTask")

                failures should containExactly(
                    TaskFailure("nonExistingTask", "Task 'nonExistingTask' does not exist.")
                )
            }
        }

        "main" should {
            "call runTasks with a database module" {
                checkMain {
                    verifyDatabaseModuleIncluded()
                }
            }
        }

        "the task module" should {
            "include a task to delete old ORT runs" {
                checkMain { koin ->
                    val task = koin.get<Task>(named("delete-old-ort-runs"))
                    task should beInstanceOf<DeleteOldOrtRunsTask>()
                }
            }

            "include a task to delete orphan entities" {
                checkMain { koin ->
                    val task = koin.get<Task>(named("delete-orphaned-entities"))
                    task should beInstanceOf<DeleteOrphanedEntitiesTask>()
                }
            }

            "include a task to delete unused projections" {
                checkMain { koin ->
                    val task = koin.get<Task>(named("delete-unused-projections"))
                    task should beInstanceOf<DeleteUnusedProjectionsTask>()
                }
            }

            "include a task for the Kubernetes job reaper" {
                checkMain { koin ->
                    val task = koin.get<Task>(named("kubernetes-reaper"))
                    task should beInstanceOf<ReaperTask>()
                }
            }

            "include a task for the Kubernetes lost jobs finder" {
                checkMain { koin ->
                    val task = koin.get<Task>(named("kubernetes-lost-jobs-finder"))
                    task should beInstanceOf<LostJobsFinderTask>()
                }
            }

            "include a task for the Kubernetes long-running jobs finder" {
                checkMain { koin ->
                    val task = koin.get<Task>(named("kubernetes-long-running-jobs-finder"))
                    task should beInstanceOf<LongRunningJobsFinderTask>()
                }
            }
        }
    }
}

/**
 * Call the task runner to execute the tasks with the given [taskNames]. Return a [TaskLog] with information about the
 * executed tasks, and the exit code of the task runner.
 */
private suspend fun checkTaskExecution(vararg taskNames: String): Pair<TaskLog, Int> {
    val taskLog = TaskLog()

    val exitCode = withTasks(*taskNames) {
        runTaskRunner(listOf(configModule(), createTestModule(taskLog)))
    }

    return taskLog to exitCode
}

/** Execute the tasks with the given [taskNames] using [runTasks] and the given [taskLog], and return the failures. */
private suspend fun runTestTasks(taskLog: TaskLog, vararg taskNames: String): List<TaskFailure> =
    withTasks(*taskNames) {
        runTasks(listOf(configModule(), createTestModule(taskLog)))
    }

/** Call the given [block] with the termination message written to the given [path]. */
private suspend fun <T> withTerminationMessagePath(path: String, block: suspend () -> T): T =
    withEnvironment("TASKS_TERMINATION_MESSAGE_PATH", path) {
        block()
    }

/** Call the given [block] with the configuration set up to run the tasks with the given [taskNames]. */
private suspend fun <T> withTasks(vararg taskNames: String, block: suspend () -> T): T =
    withEnvironment(mapOf("TASKS" to taskNames.joinToString(","))) {
        ConfigFactory.invalidateCaches()
        block()
    }

/**
 * Run a test with the `main` function. Call the given [block] with a Koin instance that is configured with the modules
 * passed to [runTasks]. The [block] can then test for the presence of certain bean instances. Set the environment
 * variable to keep the JVM alive to the given [keepAliveFlag].
 */
private suspend fun checkMain(keepAliveFlag: Boolean = true, block: (Koin) -> Unit) {
    mockkStatic(::runTasks)
    try {
        coEvery { runTasks(any()) } returns emptyList()

        val environment = mapOf(
            "ORCHESTRATOR_SENDER_TRANSPORT_TYPE" to TEST_TRANSPORT_NAME,
            "TASKS_KEEP_JVM" to keepAliveFlag.toString()
        )
        withEnvironment(environment) {
            ConfigFactory.invalidateCaches()

            main()
        }

        val captModules = slot<List<Module>>()
        coVerify { runTasks(capture(captModules)) }

        val app = startKoin {
            modules(captModules.captured)
        }
        block(app.koin)
    } finally {
        stopKoin()
        unmockkStatic(::runTasks)
    }
}

/** A simple helper class used by the test tasks to record their execution. */
private class TaskLog {
    /** A map storing the task log. */
    private val taskMap: ConcurrentMap<String, Boolean> = ConcurrentHashMap()

    /** Record in this log that the task with the given [taskName] was executed. */
    fun recordTaskExecution(taskName: String) {
        taskMap[taskName] = true
    }

    /** Return a [Set] with the names of the task that have been executed. */
    fun getExecutedTasks(): Set<String> = taskMap.keys
}

/**
 * A base class for test tasks. During execution, the task adds an entry to the [taskLog] map to record that it was
 * triggered.
 */
private open class TestTaskBase(
    /** A task name to identify the task in the task log. */
    private val name: String,

    /** A map to log which tasks were triggered. */
    private val taskLog: TaskLog
) : Task {
    override suspend fun execute() {
        taskLog.recordTaskExecution(name)
    }
}

/** The name of a task implementation that throws an exception. */
private const val ERROR_TASK = "errorTask"

/**
 * A special test task implementation that throws an exception during its execution. This is used to test the
 * exception handling of the task runner.
 */
private class ErrorTask(taskLog: TaskLog) : TestTaskBase(ERROR_TASK, taskLog) {
    override suspend fun execute() {
        super.execute()
        throw IOException("Test exception: Task execution failed.")
    }
}

/** The name of a task implementation that throws an exception with a message of several lines. */
private const val MULTILINE_ERROR_TASK = "multilineErrorTask"

/** Create a [Module] that contains all test tasks and their dependencies. */
private fun createTestModule(taskLog: TaskLog): Module =
    module {
        single { taskLog }
        single<Task>(named("task1")) { TestTaskBase("task1", get()) }
        single<Task>(named("task2")) { TestTaskBase("task2", get()) }
        single<Task>(named(ERROR_TASK)) { ErrorTask(get()) }
        single<Task>(named(MULTILINE_ERROR_TASK)) {
            object : Task {
                override suspend fun execute() = error("First line.\n  Second line.")
            }
        }
    }
