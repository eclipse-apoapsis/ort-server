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

package org.eclipse.apoapsis.ortserver.model

import kotlinx.serialization.Serializable

/**
 * The status of a Job.
 */
@Serializable
enum class JobStatus(
    /** A flag that indicates whether the status is a final status. */
    val final: Boolean,

    /** A flag that indicates whether the status is a successful status. */
    val successful: Boolean
) {
    /** The job was created in the database but not yet scheduled for execution. */
    CREATED(false, false),

    /** The job was scheduled for execution. */
    SCHEDULED(false, false),

    /** The responsible worker started processing the job. */
    RUNNING(false, false),

    /** The job failed during execution. */
    FAILED(true, false),

    /** The job was processed successfully. */
    FINISHED(true, true),

    /** The job has finished, but there were some issues over the threshold. */
    FINISHED_WITH_ISSUES(true, true);

    companion object {
        /** List of final statuses. */
        val FINAL_STATUSES = entries.filter { it.final }

        /** List of successful statuses. */
        val SUCCESSFUL_STATUSES = entries.filter { it.successful }
    }
}
