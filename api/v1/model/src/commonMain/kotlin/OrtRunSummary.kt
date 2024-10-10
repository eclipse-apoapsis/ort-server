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

package org.eclipse.apoapsis.ortserver.api.v1.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
/**
 * The summary of an ORT run.
 */
data class OrtRunSummary(
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
     * The jobs for this run.
     */
    val jobs: JobSummaries,

    /**
     * The status of this run.
     */
    val status: OrtRunStatus,

    /**
     * The labels of this run.
     */
    val labels: Options,

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
     * The optional path to a environment configuration file. If this is not defined, the environment configuration is
     * read from the default location `.ort.env.yml`.
     */
    val environmentConfigPath: String? = null
)

@Serializable
/**
 * The summaries of all jobs in an ORT run.
 */
data class JobSummaries(
    val analyzer: JobSummary? = null,
    val advisor: JobSummary? = null,
    val scanner: JobSummary? = null,
    val evaluator: JobSummary? = null,
    val reporter: JobSummary? = null
)

@Serializable
/**
 * The summary of a job.
 */
data class JobSummary(
    /**
     * The unique identifier of the job.
     */
    val id: Long,

    /**
     * The time the job was created.
     */
    val createdAt: Instant,

    /**
     * The time the job was started.
     */
    val startedAt: Instant? = null,

    /**
     * The time the job finished.
     */
    val finishedAt: Instant? = null,

    /**
     * The job status.
     */
    val status: JobStatus
)
