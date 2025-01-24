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

package org.eclipse.apoapsis.ortserver.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.MutuallyExclusiveGroupException
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.long

import io.ktor.util.cio.use
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyTo

import org.eclipse.apoapsis.ortserver.cli.utils.createOrtServerClient
import org.eclipse.apoapsis.ortserver.model.LogLevel
import org.eclipse.apoapsis.ortserver.model.LogSource

import org.ossreviewtoolkit.utils.common.expandTilde

class LogsCommand : SuspendingCliktCommand() {
    private val runId by option(
        "--run-id",
        envvar = "ORT_RUN_ID",
        help = "The ID of the ORT run."
    ).long()

    private val ortRunByIndex by OrtRunByIndexOptions().cooccurring()

    private val outputDir by option(
        "--output-dir",
        "-o",
        envvar = "ORT_DOWNLOAD_LOGS_OUTPUT_DIR",
        help = "The directory to download the logs to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val level by option(
        "--level",
        envvar = "ORT_DOWNLOAD_LOGS_LEVEL",
        help = "The log level of the logs to download, one of ${LogLevel.entries.joinToString(", ")}."
    ).enum<LogLevel>()

    private val steps by option(
        "--steps",
        envvar = "ORT_DOWNLOAD_LOGS_STEPS",
        help = "The run steps for which logs are to be retrieved, separated by commas."
    ).enum<LogSource>()
        .split(",")
        .default(emptyList())

    override fun help(context: Context) = "Download a ZIP archive with logs for a run."

    override suspend fun run() {
        if (runId != null && ortRunByIndex != null) {
            throw MutuallyExclusiveGroupException(listOf("--run-id", "--repository-id and --index"))
        }

        if (runId == null && ortRunByIndex == null) {
            throw UsageError("Either --run-id or --repository-id and --index must be provided.")
        }

        val client = createOrtServerClient() ?: throw AuthenticationError()

        val resolvedOrtRunId = runId ?: ortRunByIndex?.let {
            client.repositories.getOrtRun(it.repositoryId, it.ortRunIndex).id
        } ?: throw ProgramResult(1)

        outputDir.mkdirs()
        val resolvedLogLevel = level ?: LogLevel.INFO
        val outputFile = outputDir.resolve("run-$resolvedOrtRunId-$resolvedLogLevel.logs.zip")
        outputFile.writeChannel().use {
            client.runs.downloadLogs(resolvedOrtRunId, level, steps) { channel ->
                channel.copyTo(this)
            }
        }

        echo(outputFile.absolutePath)
    }
}
