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
    val labels: Map<String, String>
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
    val jobs: JobConfigurations,

    /**
     * The labels for this run.
     */
    val labels: Map<String, String>
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
