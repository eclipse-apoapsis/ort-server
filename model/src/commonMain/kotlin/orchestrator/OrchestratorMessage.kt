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

package org.ossreviewtoolkit.server.model.orchestrator

import kotlinx.serialization.Serializable

import org.ossreviewtoolkit.server.model.OrtRun

/**
 * Base class for the hierarchy of messages that can be processed by the Orchestrator component.
 */
@Serializable
sealed class OrchestratorMessage

/**
 * A message notifying the Orchestrator about a result produced by the Analyzer Worker.
 *
 * TODO: The exact payload still has to be defined.
 */
@Serializable
data class AnalyzerWorkerResult(
    /** The ID of the Analyzer job, as it is stored in the database. */
    val jobId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a failed Analyzer worker job.
 */
@Serializable
data class AnalyzerWorkerError(
    /** The ID of the Analyzer job, as it is stored in the database. */
    val jobId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a new ORT run.
 */
@Serializable
data class CreateOrtRun(val ortRun: OrtRun) : OrchestratorMessage()
