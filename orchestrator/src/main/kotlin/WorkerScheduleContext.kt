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

package org.eclipse.apoapsis.ortserver.orchestrator

import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.transport.MessageHeader

/**
 * A class holding context information required when scheduling new worker jobs for an [OrtRun]. This includes the
 * current [RunState] of the run and the objects needed to create and schedule new jobs.
 */
internal class WorkerScheduleContext(
    /** The object representing the current run. */
    val ortRun: OrtRun,

    /** The object to access the database. */
    val runStore: RunStore,

    /** The object for sending messages to the workers to trigger new jobs. */
    val jobDispatcher: JobDispatcher,

    /**
     * The header of the message that is currently processed. The header for messages sent to worker endpoints is
     * derived from this header.
     */
    val header: MessageHeader,

    /** The [RunState] of the current [OrtRun] which is used to decide which jobs to schedule next. */
    val runState: RunState
)
