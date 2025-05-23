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

/**
 * The status of a job.
 */
enum class JobStatus {
    /** The job was created in the database but not yet scheduled for execution. */
    CREATED,

    /** The job was scheduled for execution. */
    SCHEDULED,

    /** The responsible worker started processing the job. */
    RUNNING,

    /** The job failed during execution. */
    FAILED,

    /** The job was processed successfully. */
    FINISHED,

    /** The job has finished, but there were some issues over the threshold. */
    FINISHED_WITH_ISSUES
}
