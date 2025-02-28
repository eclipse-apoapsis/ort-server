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
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long

import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.delay

import okio.Path.Companion.toPath

import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.cli.utils.createOrtServerClient
import org.eclipse.apoapsis.ortserver.cli.utils.read
import org.eclipse.apoapsis.ortserver.client.NotFoundException

internal val POLL_INTERVAL = getEnv("POLL_INTERVAL")?.toLongOrNull()?.seconds ?: 60.seconds

class StartCommand : SuspendingCliktCommand(name = "start") {
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
                    "(see https://eclipse-apoapsis.github.io/ort-server/api/post-ort-run)."
        ).convert { it.expandTilde().toPath().read() },
        option(
            "--parameters",
            envvar = "OSC_RUNS_START_PARAMETERS",
            help = "The run configuration as a JSON string " +
                    "(see https://eclipse-apoapsis.github.io/ort-server/api/post-ort-run)."
        )
    ).required()

    override fun help(context: Context) = "Start a new run."

    override suspend fun run() {
        val createOrtRun = json.decodeFromString(CreateOrtRun.serializer(), parameters)

        val client = createOrtServerClient() ?: throw AuthenticationError()

        var ortRun = try {
            client.repositories.createOrtRun(repositoryId, createOrtRun)
        } catch (e: NotFoundException) {
            throw RepositoryNotFoundException("Repository with ID '$repositoryId' not found.", e)
        }

        ContextStorage.saveLatestRunId(ortRun.id)

        echo(json.encodeToString(ortRun))

        if (wait) {
            while (ortRun.isRunning()) {
                delay(POLL_INTERVAL)
                ortRun = client.repositories.getOrtRun(repositoryId, ortRun.index)
            }

            echo(json.encodeToString(ortRun))
        }
    }
}

private fun OrtRun.isRunning() =
    status == OrtRunStatus.ACTIVE || status == OrtRunStatus.CREATED

private class RepositoryNotFoundException(message: String, cause: Throwable) : NotFoundException(message, cause)
