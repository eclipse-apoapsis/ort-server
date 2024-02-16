/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core.api

import io.github.smiley4.ktorswaggerui.dsl.get

import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import java.util.EnumSet

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.core.apiDocs.getLogsByRunId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getReportByRunIdAndFileName
import org.eclipse.apoapsis.ortserver.core.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.core.utils.requireParameter
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.logaccess.LogFileService
import org.eclipse.apoapsis.ortserver.logaccess.LogLevel
import org.eclipse.apoapsis.ortserver.logaccess.LogSource
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.authorization.RepositoryPermission
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.services.ReportStorageService

import org.koin.ktor.ext.inject

/**
 * API for the run's endpoint. This endpoint provides information related to ORT runs and their results.
 */
fun Route.runs() = route("runs/{runId}") {
    val ortRunRepository by inject<OrtRunRepository>()

    route("reporter/{fileName}") {
        val reportStorageService by inject<ReportStorageService>()

        get(getReportByRunIdAndFileName) {
            call.forRun(ortRunRepository) { ortRun ->
                val fileName = call.requireParameter("fileName")

                requirePermission(RepositoryPermission.READ_ORT_RUNS.roleName(ortRun.repositoryId))

                val downloadData = reportStorageService.fetchReport(ortRun.id, fileName)

                call.respondOutputStream(downloadData.contentType, producer = downloadData.loader)
            }
        }
    }

    route("logs") {
        val logFileService by inject<LogFileService>()

        get(getLogsByRunId) {
            call.forRun(ortRunRepository) { ortRun ->
                requirePermission(RepositoryPermission.READ_ORT_RUNS.roleName(ortRun.repositoryId))

                val logArchive = logFileService.createLogFilesArchive(
                    ortRun.id,
                    call.extractSteps(),
                    call.extractLevel(),
                    ortRun.createdAt,
                    ortRun.finishedAt ?: Clock.System.now()
                )

                try {
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(
                            ContentDisposition.Parameters.FileName,
                            "${ortRun.id}_logs.zip"
                        ).toString()
                    )

                    call.respondFile(logArchive)
                } finally {
                    logArchive.delete()
                }
            }
        }
    }
}

/**
 * Obtain the [OrtRun] with the ID specified as `runId` parameter in this [ApplicationCall] from the given
 * [repository] and pass it as parameter to the given [handler] function. Return a 404 response if the run cannot be
 * resolved.
 */
private suspend fun ApplicationCall.forRun(repository: OrtRunRepository, handler: suspend (OrtRun) -> Unit) {
    val runId = requireParameter("runId").toLong()
    val ortRun = repository.get(runId)

    if (ortRun == null) {
        respond(HttpStatusCode.NotFound)
    } else {
        handler(ortRun)
    }
}

/**
 * Extract the parameter for the log level from this [ApplicationCall]. If this parameter is missing, return the
 * default [LogLevel.INFO]. If an invalid log level name is specified, throw a meaningful exception.
 */
private fun ApplicationCall.extractLevel(): LogLevel =
    parameters["level"]?.let { findByName<LogLevel>(it) } ?: LogLevel.INFO

/**
 * Extract the parameter for the steps for which logs are to be retrieved from this [ApplicationCall]. If this
 * parameter is missing, the logs for all workers are retrieved. Otherwise, it is interpreted as a comma-delimited
 * list of [LogSource] constants. If an invalid worker name is specified, throw a meaningful exception.
 */
private fun ApplicationCall.extractSteps(): Set<LogSource> =
    parameters["steps"]?.split(",")?.map { findByName<LogSource>(it) }?.toSet()
        ?: EnumSet.allOf(LogSource::class.java)

/**
 * Find the constant of an enum by its [name] ignoring case. Throw a meaningful exception if the name cannot be
 * resolved.
 */
private inline fun <reified E : Enum<E>> findByName(name: String): E {
    val nameUpper = name.uppercase()
    val values = enumValues<E>()

    return values.find { it.name == nameUpper } ?: throw QueryParametersException(
        "Invalid parameter value: '$name'. Allowed values are: " +
                values.joinToString(", ") { "'$it'" }
    )
}
