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

import io.github.smiley4.ktorswaggerui.dsl.routing.get

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

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApiSummary
import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToModel
import org.eclipse.apoapsis.ortserver.api.v1.model.ComparisonOperator
import org.eclipse.apoapsis.ortserver.api.v1.model.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.api.v1.model.JobSummaries
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunFilters
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty
import org.eclipse.apoapsis.ortserver.core.apiDocs.getIssuesByRunId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getLogsByRunId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrtRunById
import org.eclipse.apoapsis.ortserver.core.apiDocs.getOrtRuns
import org.eclipse.apoapsis.ortserver.core.apiDocs.getPackagesByRunId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getReportByRunIdAndFileName
import org.eclipse.apoapsis.ortserver.core.apiDocs.getRuleViolationsByRunId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getVulnerabilitiesByRunId
import org.eclipse.apoapsis.ortserver.core.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.core.authorization.requireSuperuser
import org.eclipse.apoapsis.ortserver.core.utils.pagingOptions
import org.eclipse.apoapsis.ortserver.core.utils.requireIdParameter
import org.eclipse.apoapsis.ortserver.core.utils.requireParameter
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.logaccess.LogFileService
import org.eclipse.apoapsis.ortserver.logaccess.LogLevel
import org.eclipse.apoapsis.ortserver.logaccess.LogSource
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.RuleViolationWithIdentifier
import org.eclipse.apoapsis.ortserver.model.VulnerabilityWithIdentifier
import org.eclipse.apoapsis.ortserver.model.authorization.RepositoryPermission
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.services.IssueService
import org.eclipse.apoapsis.ortserver.services.OrtRunService
import org.eclipse.apoapsis.ortserver.services.PackageService
import org.eclipse.apoapsis.ortserver.services.ReportStorageService
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.services.RuleViolationService
import org.eclipse.apoapsis.ortserver.services.VulnerabilityService

import org.koin.ktor.ext.inject

/**
 * API for the run's endpoint. This endpoint provides information related to ORT runs and their results.
 */
fun Route.runs() = route("runs") {
    val issueService by inject<IssueService>()
    val ortRunRepository by inject<OrtRunRepository>()
    val repositoryService by inject<RepositoryService>()
    val vulnerabilityService by inject<VulnerabilityService>()
    val ruleViolationService by inject<RuleViolationService>()
    val packageService by inject<PackageService>()
    val ortRunService by inject<OrtRunService>()

    get(getOrtRuns) {
        requireSuperuser()

        val pagingOptions = call.pagingOptions(SortProperty("createdAt", SortDirection.DESCENDING))
        val filters = call.filters()

        val ortRunsWithJobSummaries =
            ortRunService.listOrtRuns(
                pagingOptions.mapToModel(),
                filters.mapToModel()
            ).mapData { ortRun ->
                val jobSummaries = repositoryService.getJobs(ortRun.repositoryId, ortRun.index)?.mapToApiSummary()
                    ?: JobSummaries()
                ortRun.mapToApiSummary(jobSummaries)
            }

        val pagedSearchResponse = ortRunsWithJobSummaries
            .mapToApi { runs -> runs }
            .toSearchResponse(filters)

        call.respond(HttpStatusCode.OK, pagedSearchResponse)
    }

    route("{runId}") {
        get(getOrtRunById) { _ ->
            val ortRunId = call.requireIdParameter("runId")

            ortRunRepository.get(ortRunId)?.let { ortRun ->
                requirePermission(RepositoryPermission.READ_ORT_RUNS.roleName(ortRun.repositoryId))

                repositoryService.getJobs(ortRun.repositoryId, ortRun.index)?.let { jobs ->
                    call.respond(HttpStatusCode.OK, ortRun.mapToApi(jobs.mapToApi()))
                }
            } ?: call.respond(HttpStatusCode.NotFound)
        }

        route("logs") {
            val logFileService by inject<LogFileService>()

            get(getLogsByRunId) {
                call.forRun(ortRunRepository) { ortRun ->
                    requirePermission(RepositoryPermission.READ_ORT_RUNS.roleName(ortRun.repositoryId))

                    val sources = call.extractSteps()
                    val level = call.extractLevel()
                    val startTime = ortRun.createdAt
                    val endTime = ortRun.finishedAt ?: Clock.System.now()
                    val logArchive = logFileService.createLogFilesArchive(ortRun.id, sources, level, startTime, endTime)

                    try {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment.withParameter(
                                ContentDisposition.Parameters.FileName,
                                "run-${ortRun.id}-$level-logs.zip"
                            ).toString()
                        )

                        call.respondFile(logArchive)
                    } finally {
                        logArchive.delete()
                    }
                }
            }
        }

        route("issues") {
            get(getIssuesByRunId) {
                call.forRun(ortRunRepository) { ortRun ->
                    requirePermission(RepositoryPermission.READ_ORT_RUNS.roleName(ortRun.repositoryId))

                    val pagingOptions = call.pagingOptions(SortProperty("timestamp", SortDirection.DESCENDING))

                    val issueForOrtRun = issueService.listForOrtRunId(ortRun.id, pagingOptions.mapToModel())

                    val pagedResponse = issueForOrtRun.mapToApi(Issue::mapToApi)

                    call.respond(HttpStatusCode.OK, pagedResponse)
                }
            }
        }

        route("vulnerabilities") {
            get(getVulnerabilitiesByRunId) {
                call.forRun(ortRunRepository) { ortRun ->
                    requirePermission(RepositoryPermission.READ_ORT_RUNS.roleName(ortRun.repositoryId))

                    val pagingOptions = call.pagingOptions(SortProperty("external_id", SortDirection.ASCENDING))

                    val vulnerabilitiesForOrtRun =
                        vulnerabilityService.listForOrtRunId(ortRun.id, pagingOptions.mapToModel())

                    val pagedResponse = vulnerabilitiesForOrtRun.mapToApi(VulnerabilityWithIdentifier::mapToApi)

                    call.respond(HttpStatusCode.OK, pagedResponse)
                }
            }
        }

        route("rule-violations") {
            get(getRuleViolationsByRunId) {
                call.forRun(ortRunRepository) { ortRun ->
                    requirePermission(RepositoryPermission.READ_ORT_RUNS.roleName(ortRun.repositoryId))

                    val pagingOptions = call.pagingOptions(SortProperty("rule", SortDirection.ASCENDING))

                    val ruleViolationsForOrtRun =
                        ruleViolationService.listForOrtRunId(ortRun.id, pagingOptions.mapToModel())

                    val pagedResponse = ruleViolationsForOrtRun.mapToApi(RuleViolationWithIdentifier::mapToApi)

                    call.respond(HttpStatusCode.OK, pagedResponse)
                }
            }
        }

        route("packages") {
            get(getPackagesByRunId) {
                call.forRun(ortRunRepository) { ortRun ->
                    requirePermission(RepositoryPermission.READ_ORT_RUNS.roleName(ortRun.repositoryId))

                    val pagingOptions = call.pagingOptions(SortProperty("purl", SortDirection.ASCENDING))

                    val packagesForOrtRun = packageService.listForOrtRunId(ortRun.id, pagingOptions.mapToModel())

                    val pagedResponse = packagesForOrtRun.mapToApi(Package::mapToApi)

                    call.respond(HttpStatusCode.OK, pagedResponse)
                }
            }
        }

        route("reporter/{fileName}") {
            val reportStorageService by inject<ReportStorageService>()

            get(getReportByRunIdAndFileName) {
                call.forRun(ortRunRepository) { ortRun ->
                    val fileName = call.requireParameter("fileName")

                    requirePermission(RepositoryPermission.READ_ORT_RUNS.roleName(ortRun.repositoryId))

                    val downloadData = reportStorageService.fetchReport(ortRun.id, fileName)

                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(
                            ContentDisposition.Parameters.FileName,
                            fileName
                        ).toString()
                    )

                    call.respondOutputStream(
                        downloadData.contentType,
                        producer = downloadData.loader,
                        contentLength = downloadData.contentLength
                    )
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
internal suspend fun ApplicationCall.forRun(repository: OrtRunRepository, handler: suspend (OrtRun) -> Unit) {
    val runId = requireIdParameter("runId")
    val ortRun = repository.get(runId)

    if (ortRun == null) {
        respond(HttpStatusCode.NotFound)
    } else {
        handler(ortRun)
    }
}

/**
 * Extract the parameter for the log level from this [ApplicationCall]. If this parameter is missing or empty, return
 * the default [LogLevel.INFO]. If an invalid log level name is specified, throw a meaningful exception.
 */
private fun ApplicationCall.extractLevel(): LogLevel =
    parameters["level"]?.takeUnless { it.isEmpty() }?.let { findByName<LogLevel>(it) } ?: LogLevel.INFO

/**
 * Extract the parameter for the steps for which logs are to be retrieved from this [ApplicationCall]. If this
 * parameter is missing or empty, the logs for all workers are retrieved. Otherwise, it is interpreted as a
 * comma-delimited list of [LogSource] constants. If an invalid worker name is specified, throw a meaningful exception.
 */
private fun ApplicationCall.extractSteps(): Set<LogSource> =
    parameters["steps"]?.split(',').orEmpty()
        .map { findByName<LogSource>(it) }
        .ifEmpty { LogSource.entries }
        .toSet()

/**
 * Extract the filter for the status from this [ApplicationCall]. If this filter is missing or empty, return
 * null. Otherwise, it is interpreted as a comma-delimited list of [OrtRunStatus] constants to filter the
 * result by. If an invalid status name is specified, throw a meaningful exception. If the first item on the
 * list is a minus, the provided statuses will be excluded from the result.
 */
private fun ApplicationCall.status(): FilterOperatorAndValue<Set<OrtRunStatus>>? {
    val parts = parameters["status"]?.split(',').orEmpty()

    if (parts.isEmpty()) return null

    val operator = if (parts.first() == ("-")) ComparisonOperator.NOT_IN else ComparisonOperator.IN

    val statuses = parts
        .filter { it != "-" }
        .map { findByName<OrtRunStatus>(it) }
        .toSet()

    return FilterOperatorAndValue(
        operator,
        statuses
    )
}

/**
 * Extract the filters for the run's endpoint from this [ApplicationCall].
 */
private fun ApplicationCall.filters(): OrtRunFilters =
    OrtRunFilters(
        status = status()
    )

/**
 * Find the constant of an enum by its [name] ignoring case. Throw a meaningful exception if the name cannot be
 * resolved.
 */
private inline fun <reified E : Enum<E>> findByName(name: String): E =
    runCatching { enumValueOf<E>(name.uppercase()) }.getOrNull() ?: throw QueryParametersException(
        "Invalid parameter value: '$name'. Allowed values are: " +
                enumValues<E>().joinToString { "'$it'" }
    )
