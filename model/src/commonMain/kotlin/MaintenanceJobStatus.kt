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

package org.eclipse.apoapsis.ortserver.model

/**
 * An enum representing the status of a maintenance job.
 */
enum class MaintenanceJobStatus(val completed: Boolean) {
    /** The job is currently running. */
    STARTED(completed = false),

    /** The job has finished successfully. */
    FINISHED(completed = true),

    /** The job has failed. */
    FAILED(completed = true);

    companion object {
        val uncompletedStates = entries.filter { !it.completed }
    }
}
