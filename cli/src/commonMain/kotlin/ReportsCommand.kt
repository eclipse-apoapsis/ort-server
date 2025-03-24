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
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.long

import okio.Path.Companion.toPath

import org.eclipse.apoapsis.ortserver.cli.utils.createOrtServerClient
import org.eclipse.apoapsis.ortserver.cli.utils.mkdirs
import org.eclipse.apoapsis.ortserver.cli.utils.writeFromChannel
import org.eclipse.apoapsis.ortserver.client.NotFoundException

class ReportsCommand : SuspendingCliktCommand(name = "reports") {
    private val runId by option(
        "--run-id",
        envvar = "OSC_RUN_ID",
        help = "The ID of the ORT run, or the latest one started via $COMMAND_NAME."
    ).long()
        .withFallback(ContextStorage.get().run?.latestId)

    private val ortRunByIndex by OrtRunByIndexOptions().cooccurring()

    private val fileNames by option(
        "--file-names",
        "--filenames",
        envvar = "OSC_DOWNLOAD_REPORTS_FILE_NAMES",
        help = "The names of the files to download, separated by commas."
    ).split(",")
        .required()

    private val outputDir by option(
        "--output-dir",
        "-o",
        envvar = "OSC_DOWNLOAD_REPORTS_OUTPUT_DIR",
        help = "The directory to download the reports to."
    ).convert { it.expandTilde().toPath() }
        .required()

    override fun help(context: Context) = "Download reports for a run."

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

        fileNames.forEach { fileName ->
            val reportFile = outputDir.resolve(fileName)

            try {
                client.runs.downloadReport(resolvedOrtRunId, fileName) { reportFile.writeFromChannel(it) }

                echo(reportFile.toString())
            } catch (e: NotFoundException) {
                throw ReportNotFoundException("Report '$fileName' not found for run '$resolvedOrtRunId'.", e)
            }
        }
    }
}

private class ReportNotFoundException(message: String, cause: Throwable) : NotFoundException(message, cause)
