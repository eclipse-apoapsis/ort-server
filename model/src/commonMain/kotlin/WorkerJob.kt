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

package org.eclipse.apoapsis.ortserver.model

import kotlinx.datetime.Instant

/**
 * A common interface for all concrete worker job classes.
 *
 * The jobs have a larger number of properties in common. This interface makes it possible to access those in a
 * generic way.
 */
interface WorkerJob {
    /** The unique identifier of this job. */
    val id: Long

    /** The ID of the [OrtRun] this [WorkerJob] is a part of. */
    val ortRunId: Long

    /** The time the job was created. */
    val createdAt: Instant

    /** The time the job was started. */
    val startedAt: Instant?

    /** The time the job finished. */
    val finishedAt: Instant?

    /** The job status. */
    val status: JobStatus

    /** Job execution error message, if any. */
    val errorMessage: String?
}
