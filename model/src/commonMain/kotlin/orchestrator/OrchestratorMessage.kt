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

package org.eclipse.apoapsis.ortserver.model.orchestrator

import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.model.OrtRun

/**
 * Base class for the hierarchy of messages that can be processed by the Orchestrator component.
 */
@Serializable
sealed class OrchestratorMessage

/**
 * A message notifying the Orchestrator about a result produced by the Config worker.
 */
@Serializable
data class ConfigWorkerResult(
    /** The ID of the ORT run that was processed by the worker. */
    val ortRunId: Long
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a failed job of the Config worker.
 */
@Serializable
data class ConfigWorkerError(
    /** The ID of the ORT run on which the worker failed. */
    val ortRunId: Long
) : OrchestratorMessage()

/**
 * A common interface for messages that are sent by workers to the Orchestrator. The interface allows access to the
 * job ID of the affected worker. This can be used to handle such messages in a generic way. Note that the Config
 * worker is a bit special here; it does only initial preparations, but does not have its own job table.
 */
interface WorkerMessage {
    val jobId: Long
}

/**
 * A message notifying the Orchestrator about a result produced by the Analyzer Worker.
 */
@Serializable
data class AnalyzerWorkerResult(
    /** The ID of the Analyzer job, as it is stored in the database. */
    override val jobId: Long,
    /** if `true`, the result has issues over the threshold. */
    val hasIssues: Boolean = false
) : OrchestratorMessage(), WorkerMessage

/**
 * A message notifying the Orchestrator about a failed Analyzer worker job.
 */
@Serializable
data class AnalyzerWorkerError(
    /** The ID of the Analyzer job, as it is stored in the database. */
    override val jobId: Long
) : OrchestratorMessage(), WorkerMessage

/**
 * A message notifying the Orchestrator about a result produced by the Advisor Worker.
 */
@Serializable
data class AdvisorWorkerResult(
    /** The ID of the Advisor job, as it is stored in the database. */
    override val jobId: Long,
    /** if `true`, the result has issues over the threshold. */
    val hasIssues: Boolean = false
) : OrchestratorMessage(), WorkerMessage

/**
 * A message notifying the Orchestrator about a failed Advisor worker job.
 */
@Serializable
data class AdvisorWorkerError(
    /** The ID of the Advisor job, as it is stored in the database. */
    override val jobId: Long
) : OrchestratorMessage(), WorkerMessage

/**
 * A message notifying the Orchestrator about a result produced by the Scanner Worker.
 */
@Serializable
data class ScannerWorkerResult(
    /** The ID of the Scanner job, as it is stored in the database. */
    override val jobId: Long,
    /** if `true`, the result has issues over the threshold. */
    val hasIssues: Boolean = false
) : OrchestratorMessage(), WorkerMessage

/**
 * A message notifying the Orchestrator about a failed Scanner worker job.
 */
@Serializable
data class ScannerWorkerError(
    /** The ID of the Scanner job, as it is stored in the database. */
    override val jobId: Long
) : OrchestratorMessage(), WorkerMessage

/**
 * A message notifying the Orchestrator about a result produced by the Evaluator Worker.
 */
@Serializable
data class EvaluatorWorkerResult(
    /** The ID of the Evaluator job, as it is stored in the database. */
    override val jobId: Long,
    /** if `true`, the result has issues over the threshold. */
    val hasIssues: Boolean = false
) : OrchestratorMessage(), WorkerMessage

/**
 * A message notifying the Orchestrator about a failed Evaluator worker job.
 */
@Serializable
data class EvaluatorWorkerError(
    /** The ID of the Evaluator job, as it is stored in the database. */
    override val jobId: Long
) : OrchestratorMessage(), WorkerMessage

/**
 * A message notifying the Orchestrator about a result produced by the Reporter Worker.
 */
@Serializable
data class ReporterWorkerResult(
    /** The ID of the Reporter job, as it is stored in the database. */
    override val jobId: Long,
    /** if `true`, the result has issues over the threshold. */
    val hasIssues: Boolean = false
) : OrchestratorMessage(), WorkerMessage

/**
 * A message notifying the Orchestrator about a failed Reporter worker job.
 */
@Serializable
data class ReporterWorkerError(
    /** The ID of the Reporter job, as it is stored in the database. */
    override val jobId: Long
) : OrchestratorMessage(), WorkerMessage

/**
 * A message notifying the Orchestrator about a result produced by the Notifier Worker.
 */
@Serializable
data class NotifierWorkerResult(
    /** The ID of the Notifier job, as it is stored in the database. */
    override val jobId: Long
) : OrchestratorMessage(), WorkerMessage

/**
 * A message notifying the Orchestrator about a failed Notifier worker job.
 */
@Serializable
data class NotifierWorkerError(
    /** The ID of the Notifier job, as it is stored in the database. */
    override val jobId: Long
) : OrchestratorMessage(), WorkerMessage

/**
 * A message notifying the Orchestrator about a new ORT run.
 */
@Serializable
data class CreateOrtRun(val ortRun: OrtRun) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about a (critical) error of a worker. This error means that there was a
 * fatal crash during job processing which even prevents the affected endpoint from sending a proper error message.
 * Therefore, only limited error information is available.
 */
@Serializable
data class WorkerError(
    /** The name of the endpoint where the error has happened. */
    val endpointName: String,
) : OrchestratorMessage()

/**
 * A message notifying the Orchestrator about found OrtRun either has no jobs scheduled or started, or jobs finished
 * with success, but whole OrtRun is still in ACTIVE state.
 */
@Serializable
data class OrtRunStuckJobsError(
    val ortRunId: Long
) : OrchestratorMessage()
