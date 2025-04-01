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

package org.eclipse.apoapsis.ortserver.cli.model.printables

import com.github.ajalt.mordant.rendering.TextColors.blue
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.GridBuilder
import com.github.ajalt.mordant.table.grid

import org.eclipse.apoapsis.ortserver.api.v1.model.JobStatus
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.cli.json

/**
 * A [CliPrintable] for a formatted and reduced [OrtRun] object.
 */
class OrtRunPrintable(private val ortRun: OrtRun) : CliPrintable {
    override fun humanReadable(): Widget {
        return grid {
            row(yellow("ORT Run Information"))
            formattedRow("ID", ortRun.id)
            formattedRow("Repository ID", ortRun.repositoryId)
            formattedRow("Index", ortRun.index)
            formattedStatusRow("Status", ortRun.status)
            formattedRow("Created", ortRun.createdAt)
            formattedRow("Revision", ortRun.revision)
            ortRun.path?.takeIf { it.isNotBlank() }?.also { formattedRow("Path", it) }
            ortRun.userDisplayName?.takeIf { it.username.isNotBlank() }
                ?.also { formattedRow("User", "${it.fullName} (${it.username})") }
            ortRun.jobConfigContext?.takeIf { it.isNotBlank() }?.also { formattedRow("Config Context", it) }

            row()
            if (ortRun.hasAnyJobs()) {
                row(yellow("Job Status"))
                ortRun.jobs.analyzer?.status?.also { formattedStatusRow("Analyzer Status", it) }
                ortRun.jobs.advisor?.status?.also { formattedStatusRow("Advisor Status", it) }
                ortRun.jobs.scanner?.status?.also { formattedStatusRow("Scanner Status", it) }
                ortRun.jobs.evaluator?.status?.also { formattedStatusRow("Evaluator Status", it) }
                ortRun.jobs.reporter?.status?.also { formattedStatusRow("Reporter Status", it) }
                ortRun.jobs.notifier?.status?.also { formattedStatusRow("Notifier Status", it) }
            }
        }
    }

    override fun json() = json.encodeToString(ortRun)
}

fun OrtRun.toPrintable(): OrtRunPrintable = OrtRunPrintable(this)

private fun GridBuilder.formattedRow(name: String, value: Any) {
    row(blue(name), value.toString()) {
        padding { left = 2 }
     }
}

private fun <T : Enum<T>> GridBuilder.formattedStatusRow(name: String, status: T) {
    val color = when (status) {
        is OrtRunStatus -> when (status) {
            OrtRunStatus.CREATED, OrtRunStatus.ACTIVE -> yellow
            OrtRunStatus.FINISHED -> green
            OrtRunStatus.FAILED -> red
            OrtRunStatus.FINISHED_WITH_ISSUES -> cyan
        }
        is JobStatus -> when (status) {
            JobStatus.CREATED, JobStatus.SCHEDULED, JobStatus.RUNNING -> yellow
            JobStatus.FINISHED -> green
            JobStatus.FAILED -> red
            JobStatus.FINISHED_WITH_ISSUES -> cyan
        }
        else -> null
    }

    val stringStatus = status.toString()
    if (color != null) {
        row(blue(name), color(stringStatus)) { padding { left = 2 } }
    } else {
        row(blue(name), stringStatus) { padding { left = 2 } }
    }
}

private fun OrtRun.hasAnyJobs() =
    jobs.analyzer != null ||
    jobs.advisor != null ||
    jobs.scanner != null ||
    jobs.evaluator != null ||
    jobs.reporter != null ||
    jobs.notifier != null
