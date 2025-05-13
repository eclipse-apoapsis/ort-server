/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.api.v1.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class OrtRun(
    /**
     * The unique identifier.
     */
    val id: Long,

    /**
     * The index of this ORT run for the affected repository. Together with the [repositoryId], this property uniquely
     * identifies an ORT run.
     */
    val index: Long,

    /**
     * The id of the [Organization] this run belongs to.
     */
    val organizationId: Long,

    /**
     * The id of the [Product] this run belongs to.
     */
    val productId: Long,

    /**
     * The id of the repository for this run.
     */
    val repositoryId: Long,

    /**
     * The repository revision used by this run.
     */
    val revision: String,

    /**
     * The revision resolved from [revision].
     */
    val resolvedRevision: String? = null,

    /**
     * The optional VCS sub-path of the project repository, which should be downloaded instead of the whole repository.
     * If this is not specified, the entire repository will be downloaded.
     */
    val path: String? = null,

    /**
     * The time this run was created.
     */
    val createdAt: Instant,

    /**
     * The time when this run was finished or *null* if it is not yet finished.
     */
    val finishedAt: Instant? = null,

    /**
     * The job configurations for this run.
     */
    val jobConfigs: JobConfigurations,

    /**
     * The resolved job configurations for this run. This field stores the output of the parameters check and validation
     * script.
     */
    val resolvedJobConfigs: JobConfigurations? = null,

    /**
     * The jobs for this run.
     */
    val jobs: Jobs,

    /**
     * The status of this run.
     */
    val status: OrtRunStatus,

    /**
     * The labels of this run.
     */
    val labels: Options,

    /**
     * A list with issues that have been found for this run and that are not related to one of the processing steps.
     * Such issues are created for instance during validation of the run parameters.
     */
    val issues: List<Issue>,

    /**
     * An optional context to be used when obtaining configuration for this ORT run. This context is passed to the
     * configuration manager and can be used to select a specific subset or a version of configuration properties. If
     * this value is missing, the default configuration context should be used.
     */
    val jobConfigContext: String? = null,

    /**
     * The resolved configuration context. When an ORT run is started, the configuration context is resolved once and
     * then stored, so that all workers access the same set of configuration properties.
     */
    val resolvedJobConfigContext: String? = null,

    /**
     * The optional path to an environment configuration file. If this is not defined, the environment configuration is
     * read from the default location `.ort.env.yml`.
     */
    val environmentConfigPath: String? = null,

    /**
     * The trace ID that is assigned to this run. This is generated when the run is created. It can be used to
     * correlate the logs from different components that are taking part in processing of the run.
     */
    val traceId: String?,

    /**
     * The display name of the user that triggered the scan.
     */
    val userDisplayName: UserDisplayName? = null
)

/**
 * Request object for the create ort run endpoint.
 */
@Serializable
data class CreateOrtRun(
    /**
     * The repository revision used by this run.
     */
    val revision: String,

    /**
     * The optional VCS sub-path of the project repository, which should be downloaded instead of the whole repository.
     * If this is not specified, the entire repository will be downloaded.
     */
    val path: String? = null,

    /**
     * The job configurations for this run.
     */
    val jobConfigs: JobConfigurations,

    /**
     * The labels for this run.
     */
    val labels: Options? = emptyMap(),

    /**
     * The optional context for obtaining the configuration of this run.
     */
    val jobConfigContext: String? = null,

    /**
     * The optional path to an environment configuration file. If this is not defined, the environment configuration is
     * read from the default location `.ort.env.yml`.
     */
    val environmentConfigPath: String? = null,

    /**
     * The IDs of the repositories for this run.
     */
    val repositoryIds: List<Long> = emptyList(),

    /**
     * The IDs of the repositories for this run that failed.
     */
    val repositoryFailedIds: List<Long> = emptyList()
)

enum class OrtRunStatus {
    CREATED,
    ACTIVE,
    FINISHED,
    FAILED,
    FINISHED_WITH_ISSUES
}

@Serializable
data class Jobs(
    val analyzer: AnalyzerJob? = null,
    val advisor: AdvisorJob? = null,
    val scanner: ScannerJob? = null,
    val evaluator: EvaluatorJob? = null,
    val reporter: ReporterJob? = null,
    val notifier: NotifierJob? = null
)

/**
 * Object containing values to filter an ort run listing with.
 */
@Serializable
data class OrtRunFilters(
    val status: FilterOperatorAndValue<Set<OrtRunStatus>>? = null
)
