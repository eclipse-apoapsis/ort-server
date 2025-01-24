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
import com.github.ajalt.clikt.core.MutuallyExclusiveGroupException
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long

import org.eclipse.apoapsis.ortserver.client.OrtServerClient

class InfoCommand(private val config: OrtServerOptions) : SuspendingCliktCommand(name = "info") {
    private val runId by option(
        "--run-id",
        envvar = "ORT_RUN_ID",
        help = "The ID of the ORT run."
    ).long()

    private val ortRunByIndex by OrtRunByIndexOptions().cooccurring()

    override fun help(context: Context) = "Print information about a run."

    override suspend fun run() {
        if (runId != null && ortRunByIndex != null) {
            throw MutuallyExclusiveGroupException(listOf("--run-id", "--repository-id and --index"))
        }

        if (runId == null && ortRunByIndex == null) {
            throw UsageError("Either --run-id or --repository-id and --index must be provided.")
        }

        val client = OrtServerClient.create(config.toOrtServerClientConfig())

        val ortRun = runId?.let {
            client.runs.getOrtRun(it)
        } ?: ortRunByIndex?.let {
            client.repositories.getOrtRun(it.repositoryId, it.ortRunIndex)
        } ?: throw ProgramResult(1)

        echo(json.encodeToString(ortRun))
    }
}

class OrtRunByIndexOptions : OptionGroup() {
    val repositoryId by option(
        "--repository-id",
        envvar = "REPOSITORY_ID",
        help = "The ID of the repository."
    ).long().required()

    val ortRunIndex by option(
        "--index",
        envvar = "ORT_RUN_INDEX",
        help = "The index of the ORT run."
    ).long().required()
}
