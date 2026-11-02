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
import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerRequest
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.ConfigEndpoint
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EvaluatorEndpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.MessagePublisher
import org.eclipse.apoapsis.ortserver.transport.NotifierEndpoint
import org.eclipse.apoapsis.ortserver.transport.ReporterEndpoint
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint
import org.eclipse.apoapsis.ortserver.transport.selectByPrefix

/**
 * A class that sends messages to the worker endpoints to trigger the processing of jobs. This is the only place in
 * the Orchestrator that publishes messages.
 */
internal class JobDispatcher(
    /** The object for sending messages to the workers. */
    private val publisher: MessagePublisher
) {
    /** Send a message derived from the given [header] to the Config worker to process the given [run]. */
    fun dispatchConfigJob(run: OrtRun, header: MessageHeader) {
        publish(ConfigEndpoint, run, header, ConfigRequest(run.id))
    }

    /**
     * Send a message derived from the given [header] to the given [worker] to process the job with the given [jobId]
     * of the given [run].
     */
    fun dispatchJob(worker: WorkerScheduleInfo, jobId: Long, run: OrtRun, header: MessageHeader) {
        when (worker) {
            WorkerScheduleInfo.ANALYZER -> publish(AnalyzerEndpoint, run, header, AnalyzerRequest(jobId))
            WorkerScheduleInfo.ADVISOR -> publish(AdvisorEndpoint, run, header, AdvisorRequest(jobId))
            WorkerScheduleInfo.SCANNER -> publish(ScannerEndpoint, run, header, ScannerRequest(jobId))
            WorkerScheduleInfo.EVALUATOR -> publish(EvaluatorEndpoint, run, header, EvaluatorRequest(jobId))
            WorkerScheduleInfo.REPORTER -> publish(ReporterEndpoint, run, header, ReporterRequest(jobId))
            WorkerScheduleInfo.NOTIFIER -> publish(NotifierEndpoint, run, header, NotifierRequest(jobId))
        }
    }

    /**
     * Create a message based on the given [run], [header], and [payload] and publish it to the given [endpoint].
     * Make sure that the header contains the correct transport properties. These are obtained from the labels of the
     * [run]. Also make sure that a trace ID is available.
     */
    private fun <T : Any> publish(endpoint: Endpoint<T>, run: OrtRun, header: MessageHeader, payload: T) {
        val traceId = header.traceId.takeUnless { it.isEmpty() } ?: run.traceId.orEmpty()
        val headerWithProperties = header.copy(
            traceId = traceId,
            transportProperties = run.labels.selectByPrefix("transport")
        )

        publisher.publish(to = endpoint, message = Message(headerWithProperties, payload))
    }
}
