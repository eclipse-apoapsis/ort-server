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
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.inputStream
import com.github.ajalt.clikt.parameters.types.long

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.delay

import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.client.OrtServerClient

internal val POLL_INTERVAL = System.getProperty("POLL_INTERVAL")?.toLongOrNull()?.milliseconds ?: 60.seconds

class StartCommand(private val config: OrtServerOptions) : SuspendingCliktCommand(name = "start") {
    private val repositoryId by option(
        "--repository-id",
        envvar = "REPOSITORY_ID",
        help = "The ID of the repository."
    ).long().required()

    private val wait by option(
        "--wait",
        envvar = "WAIT",
        help = "Wait for the run to finish."
    ).flag()

    private val parameters by mutuallyExclusiveOptions(
        option(
            "--parameters-file",
            envvar = "ORT_RUNS_START_PARAMETERS_FILE",
            help = "The path to the Create ORT run configuration file!"
        ).inputStream().convert { it.bufferedReader().readText() },
        option(
            "--parameters",
            envvar = "ORT_RUNS_START_PARAMETERS",
            help = "The Create ORT run configuration as a string."
        )
    ).required()

    override suspend fun run() {
        val createOrtRun = json.decodeFromString(CreateOrtRun.serializer(), parameters)

        val client = OrtServerClient.create(config.toOrtServerClientConfig())

        var ortRun = client.repositories.createOrtRun(
            repositoryId = repositoryId,
            ortRun = createOrtRun
        )

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
