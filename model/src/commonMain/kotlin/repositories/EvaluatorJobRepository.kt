/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.model.repositories

import kotlinx.datetime.Instant

import org.ossreviewtoolkit.server.model.EvaluatorJob
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.util.OptionalValue

/**
 * A repository of [evaluator jobs][EvaluatorJob].
 */
interface EvaluatorJobRepository {
    /**
     * Create an evaluator job.
     */
    fun create(ortRunId: Long, configuration: EvaluatorJobConfiguration): EvaluatorJob

    /**
     * Get an evaluator job by [id]. Returns null if the evaluator job is not found.
     */
    fun get(id: Long): EvaluatorJob?

    /**
     * Get the evaluator job for an [ORT run][ortRunId].
     */
    fun getForOrtRun(ortRunId: Long): EvaluatorJob?

    /**
     * Update an evaluator job by [id] with the [present][OptionalValue.Present] values.
     */
    fun update(
        id: Long,
        startedAt: OptionalValue<Instant?> = OptionalValue.Absent,
        finishedAt: OptionalValue<Instant?> = OptionalValue.Absent,
        status: OptionalValue<JobStatus> = OptionalValue.Absent
    ): EvaluatorJob

    /**
     * Delete an evaluator job by [id].
     */
    fun delete(id: Long)
}
