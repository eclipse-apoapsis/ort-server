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

package org.eclipse.apoapsis.ortserver.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.mordant.animation.coroutines.animateInCoroutine
import com.github.ajalt.mordant.widgets.Spinner
import com.github.ajalt.mordant.widgets.progress.percentage
import com.github.ajalt.mordant.widgets.progress.progressBar
import com.github.ajalt.mordant.widgets.progress.progressBarContextLayout
import com.github.ajalt.mordant.widgets.progress.spinner
import com.github.ajalt.mordant.widgets.progress.text

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

import okio.Path.Companion.toPath

import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.api.v1.model.PostRepositoryRun
import org.eclipse.apoapsis.ortserver.cli.model.AuthenticationError
import org.eclipse.apoapsis.ortserver.cli.model.CliInputException
import org.eclipse.apoapsis.ortserver.cli.model.RunFinishedWithIssuesException
import org.eclipse.apoapsis.ortserver.cli.model.printables.toPrintable
import org.eclipse.apoapsis.ortserver.cli.utils.createAuthenticatedOrtServerClient
import org.eclipse.apoapsis.ortserver.cli.utils.echoMessage
import org.eclipse.apoapsis.ortserver.cli.utils.read
import org.eclipse.apoapsis.ortserver.cli.utils.useJsonFormat
import org.eclipse.apoapsis.ortserver.client.NotFoundException
import org.eclipse.apoapsis.ortserver.client.OrtServerClient

/**
 * The maximum number of failures in a sequence that cause the waiting loop for a run to finish to abort.
 */
private const val MAX_FAILURE_COUNT = 10

class StartCommand : SuspendingCliktCommand(name = "start") {
    companion object {
        /**
         * The interval to poll the server for updates when waiting for a run to finish. This is modifiable to allow
         * tests to run faster.
         */
        private var pollInterval = 10.seconds

        /**
         * Set the polling interval to use when waiting for a run to finish. This is only intended to be used in tests.
         */
        internal fun setPollInterval(interval: Duration) {
            pollInterval = interval
        }
    }

    private val repositoryId by option(
        "--repository-id",
        envvar = "OSC_REPOSITORY_ID",
        help = "The ID of the repository."
    ).long().required()

    private val wait by option(
        "--wait",
        envvar = "OSC_RUNS_START_WAIT",
        help = "Wait for the run to finish."
    ).flag()

    private val parameters by mutuallyExclusiveOptions(
        option(
            "--parameters-file",
            envvar = "OSC_RUNS_START_PARAMETERS_FILE",
            help = "The path to a JSON file containing the run configuration " +
                    "(see https://eclipse-apoapsis.github.io/ort-server/api/post-repository-run).",
            completionCandidates = CompletionCandidates.Path
        ).convert { it.expandTilde().toPath().read() },
        option(
            "--parameters",
            envvar = "OSC_RUNS_START_PARAMETERS",
            help = "The run configuration as a JSON string " +
                    "(see https://eclipse-apoapsis.github.io/ort-server/api/post-repository-run)."
        )
    ).required()

    override fun help(context: Context) = "Start a new run."

    override suspend fun run() {
        val createOrtRun = runCatching {
            json.decodeFromString<PostRepositoryRun>(parameters)
        }.getOrElse {
            throw CliInputException(
                "Invalid run parameters: '$parameters'.",
                cause = it
            )
        }

        val client = createAuthenticatedOrtServerClient() ?: throw AuthenticationError()

        var ortRun = try {
            client.repositories.createOrtRun(repositoryId, createOrtRun)
        } catch (e: NotFoundException) {
            throw RepositoryNotFoundException("Repository with ID '$repositoryId' not found.", e)
        }

        ContextStorage.saveLatestRunId(ortRun.id)

        if (wait) {
            var failureCount = 0

            if (useJsonFormat) {
                while (ortRun.isRunning()) {
                    delay(pollInterval)
                    val update = runCatching { client.repositories.getOrtRun(repositoryId, ortRun.index) }
                    ortRun = when {
                        update.isSuccess -> {
                            failureCount = 0
                            update.getOrThrow()
                        }
                        update.isFailure && failureCount >= MAX_FAILURE_COUNT -> {
                            echoMessage("Aborting after $MAX_FAILURE_COUNT consecutive errors.")
                            update.getOrThrow()
                        }
                        else -> {
                            failureCount++
                            echoMessage("Warning: An error occurred while polling the run status. Retrying...")
                            ortRun
                        }
                    }
                }
            } else {
                echoMessage(ortRun.toPrintable())
                ortRun = updateProgressBar(ortRun, client)
            }
        }

        echoMessage(ortRun.toPrintable())

        if (ortRun.status == OrtRunStatus.FAILED || ortRun.status == OrtRunStatus.FINISHED_WITH_ISSUES) {
            throw RunFinishedWithIssuesException("The ORT run finished with status '${ortRun.status}'.")
        }
    }

    private fun updateProgressBar(ortRun: OrtRun, client: OrtServerClient): OrtRun = runBlocking {
        val maxProgressBarValue = JobType.entries.count { it.isConfigured(ortRun.jobConfigs) }.withInitialState()

        val progress = progressBarContextLayout {
            text { context }
            spinner(Spinner.Dots())
            progressBar(width = 30)
            percentage()
        }.animateInCoroutine(
            terminal,
            context = "Starting",
            total = maxProgressBarValue,
            completed = 0
        )

        launch { progress.execute() }

        var updatedOrtRun = ortRun.copy()
        while (updatedOrtRun.isRunning()) {
            delay(pollInterval)
            updatedOrtRun = client.repositories.getOrtRun(repositoryId, updatedOrtRun.index)
            progress.update {
                context =
                    JobType.entries.reversed().find { it.isStarted(updatedOrtRun.jobs) }?.displayName ?: "Starting"
                completed = JobType.entries.count { it.isFinished(updatedOrtRun.jobs) }.withInitialState()
            }
        }

        progress.update {
            context = "Finished"
            completed = maxProgressBarValue
        }

        updatedOrtRun
    }

    private enum class JobType(val displayName: String) {
        ANALYZER("Analyzing"),
        ADVISOR("Advising"),
        SCANNER("Scanning"),
        EVALUATOR("Evaluating"),
        REPORTER("Reporting"),
        NOTIFIER("Notifying");

        fun isConfigured(config: JobConfigurations) = when (this) {
            ANALYZER -> true
            ADVISOR -> config.advisor != null
            SCANNER -> config.scanner != null
            EVALUATOR -> config.evaluator != null
            REPORTER -> config.reporter != null
            NOTIFIER -> config.notifier != null
        }

        fun isStarted(jobs: Jobs) = when (this) {
            ANALYZER -> jobs.analyzer != null
            ADVISOR -> jobs.advisor != null
            SCANNER -> jobs.scanner != null
            EVALUATOR -> jobs.evaluator != null
            REPORTER -> jobs.reporter != null
            NOTIFIER -> jobs.notifier != null
        }

        fun isFinished(jobs: Jobs) = when (this) {
            ANALYZER -> jobs.analyzer?.finishedAt != null
            ADVISOR -> jobs.advisor?.finishedAt != null
            SCANNER -> jobs.scanner?.finishedAt != null
            EVALUATOR -> jobs.evaluator?.finishedAt != null
            REPORTER -> jobs.reporter?.finishedAt != null
            NOTIFIER -> jobs.notifier?.finishedAt != null
        }
    }

    /**
     * The first job status change is 'Starting' -> 'Analyzing'.
     * Add the artificial 'Starting' state to the maximum possible progress bar value to correctly represent this in the
     * progress bar.
     */
    private fun Int.withInitialState(): Long = (this + 1).toLong()
}

private fun OrtRun.isRunning() =
    status == OrtRunStatus.ACTIVE || status == OrtRunStatus.CREATED

private class RepositoryNotFoundException(message: String, cause: Throwable) : NotFoundException(message, cause)
