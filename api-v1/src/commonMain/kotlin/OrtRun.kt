/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.api.v1

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
     * The id of the repository for this run.
     */
    val repositoryId: Long,

    /**
     * The repository revision used by this run.
     */
    val revision: String,

    /**
     * The time this run was created.
     */
    val createdAt: Instant,

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
    val labels: Map<String, String>,

    /**
     * A list with issues that have been found for this run and that are not related to one of the processing steps.
     * Such issues are created for instance during validation of the run parameters.
     */
    val issues: List<OrtIssue>,

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
    val resolvedJobConfigContext: String? = null
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
     * The job configurations for this run.
     */
    val jobConfigs: JobConfigurations,

    /**
     * The labels for this run.
     */
    val labels: Map<String, String>,

    /**
     * The optional context for obtaining the configuration of this run.
     */
    val jobConfigContext: String? = null
)

@Serializable
enum class OrtRunStatus {
    CREATED,
    ACTIVE,
    FINISHED,
    FAILED
}

@Serializable
data class Jobs(
    val analyzer: AnalyzerJob? = null,
    val advisor: AdvisorJob? = null,
    val scanner: ScannerJob? = null,
    val evaluator: EvaluatorJob? = null,
    val reporter: ReporterJob? = null
)
