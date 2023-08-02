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

package org.ossreviewtoolkit.server.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

import org.ossreviewtoolkit.server.model.runs.OrtIssue

@Serializable
data class OrtRun(
    /**
     * The unique identifier.
     */
    val id: Long,

    /**
     * The index of this run in the [repository][repositoryId].
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
    val config: JobConfigurations,

    /**
     * The resolved job configurations for this run. This field stores the output of the parameters check and validation
     * script.
     */
    val resolvedConfig: JobConfigurations?,

    /**
     * The status of this run.
     */
    val status: OrtRunStatus,

    /**
     * The labels of this run.
     */
    val labels: Map<String, String>,

    /**
     * Original VCS-related information containing the analyzer root.
     */
    val vcsId: Long?,

    /**
     * Processed VCS-related information containing the analyzer root.
     */
    val vcsProcessedId: Long?,

    /**
     * A map of nested repositories, for example Git submodules or Git-Repo modules. The key is the path to the
     * nested repository relative to the root of the main repository and the value is the id of its VCS information.
     */
    val nestedRepositoryIds: Map<String, Long>?,

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
    val configContext: String?,

    /**
     * The resolved configuration context. When an ORT run is started, the configuration context is resolved once and
     * then stored, so that all workers access the same set of configuration properties.
     */
    val resolvedConfigContext: String?
)

enum class OrtRunStatus {
    CREATED,
    ACTIVE,
    FINISHED,
    FAILED
}
