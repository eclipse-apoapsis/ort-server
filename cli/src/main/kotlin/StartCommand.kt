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
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.delay

import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations
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

    private val revision by option(
        "--vcs-revision",
        envvar = "VCS_REVISION",
        help = "Revision of the repository to start the run for."
    ).required()

    private val path by option(
        "--vcs-sub-path",
        envvar = "VCS_SUB_PATH",
        help = "Optional VCS sub-path of the repository."
    )

    private val jobConfigContext by option(
        "--job-config-context",
        envvar = "JOB_CONFIG_CONTEXT",
        help = "Configuration context to use for the run."
    )

    private val wait by option(
        "--wait",
        envvar = "WAIT",
        help = "Wait for the run to finish."
    ).flag()

    override suspend fun run() {
        val client = OrtServerClient.create(config.toOrtServerClientConfig())

        var ortRun = client.repositories.createOrtRun(
            repositoryId = repositoryId,
            ortRun = CreateOrtRun(
                revision = revision,
                path = path,
                jobConfigs = JobConfigurations(),
                jobConfigContext = jobConfigContext
            )
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
