/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.orchestrator

import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.transport.MessageHeader

/**
 * A class describing the messages to send to the workers after the transaction that updated the state of an ORT run
 * has been committed. Messages are only sent after the commit, so that workers never see uncommitted jobs.
 */
internal data class SchedulingPlan(
    /** The ORT run the plan is for. */
    val ortRun: OrtRun,

    /** The header of the message that is currently processed, used to derive the headers of new messages. */
    val header: MessageHeader,

    /** The IDs of the jobs that have been created and must be dispatched to their workers. */
    val jobs: Map<WorkerScheduleInfo, Long> = emptyMap(),

    /** A flag whether the Config worker must be triggered for the run. */
    val dispatchConfigJob: Boolean = false
)
