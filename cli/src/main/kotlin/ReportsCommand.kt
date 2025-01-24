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
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.long

import io.ktor.util.cio.use
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyTo

import java.io.File

import org.eclipse.apoapsis.ortserver.client.OrtServerClient

import org.ossreviewtoolkit.utils.common.expandTilde

class ReportsCommand(private val config: OrtServerOptions) : SuspendingCliktCommand(name = "reports") {
    private val runId by option(
        "--run-id",
        envvar = "ORT_RUN_ID",
        help = "The ID of the ORT run."
    ).long()

    private val ortRunByIndex by OrtRunByIndexOptions().cooccurring()

    private val fileNames by option(
        "--file-names",
        "--filenames",
        envvar = "ORT_DOWNLOAD_REPORTS_FILE_NAMES",
        help = "The names of the files to download, separated by commas."
    ).split(",")
        .required()

    private val outputDir by option(
        "--output-dir",
        "-o",
        envvar = "ORT_DOWNLOAD_REPORTS_OUTPUT_DIR",
        help = "The directory to download the reports to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = true, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun help(context: Context) = "Download reports for a run."

    override suspend fun run() {
        if (runId != null && ortRunByIndex != null) {
            throw MutuallyExclusiveGroupException(listOf("--run-id", "--repository-id and --index"))
        }

        if (runId == null && ortRunByIndex == null) {
            throw UsageError("Either --run-id or --repository-id and --index must be provided.")
        }

        val client = OrtServerClient.create(config.toOrtServerClientConfig())
        val resolvedOrtRunId = runId ?: ortRunByIndex?.let {
            client.repositories.getOrtRun(it.repositoryId, it.ortRunIndex).id
        } ?: throw ProgramResult(1)

        outputDir.mkdirs()

        fileNames.forEach { fileName ->
            val reportFile = File(outputDir, fileName)
            reportFile.writeChannel().use {
                client.runs.downloadReport(resolvedOrtRunId, fileName) { channel ->
                    channel.copyTo(this)
                }
            }

            echo(reportFile.absolutePath)
        }
    }
}
