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

package org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes

import io.kubernetes.client.openapi.models.V1Job

import org.eclipse.apoapsis.ortserver.model.ActiveOrtRun
import org.eclipse.apoapsis.ortserver.model.orchestrator.LostSchedule
import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.model.orchestrator.WorkerError
import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.JobHandler.Companion.ortRunId
import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.JobHandler.Companion.traceId
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.MessageSender
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(FailedJobNotifier::class.java)

/**
 * A helper class that sends a message to the Orchestrator about a failed job. The content of this message is extracted
 * from the job's metadata.
 */
internal class FailedJobNotifier(
    /** The sender for sending messages to the Orchestrator. */
    private val sender: MessageSender<OrchestratorMessage>
) {
    fun sendFailedJobNotification(job: V1Job) {
        val endpointName = job.metadata?.name?.substringBefore('-')
        if (endpointName != null) {
            val traceId = job.traceId()
            val ortRunId = job.ortRunId

            if (traceId.isNotEmpty() && ortRunId != null) {
                val header = MessageHeader(traceId = traceId, ortRunId)
                val message = Message(header, WorkerError(endpointName))

                sendToOrchestrator(message)
            }
        }
    }

    /**
     * Send a notification about a lost job for the given [ortRunId] and [endpoint]. This is used to notify the
     * Orchestrator about jobs that disappeared in Kubernetes.
     */
    fun sendLostJobNotification(ortRunId: Long, endpoint: Endpoint<*>) {
        val header = MessageHeader(traceId = "", ortRunId)
        val message = Message(header, WorkerError(endpoint.configPrefix))

        sendToOrchestrator(message)
    }

    /**
     * Send a notification about an ORT run without active schedules for the given [ortRun]. This is used to notify
     * the Orchestrator that it has to reschedule jobs for the affected run.
     */
    fun sendLostScheduleNotification(ortRun: ActiveOrtRun) {
        val header = MessageHeader(ortRun.traceId.orEmpty(), ortRun.runId)
        val message = Message(header, LostSchedule(ortRun.runId))

        sendToOrchestrator(message)
    }

    /**
     * Send the given [message] to the Orchestrator via the configured [MessageSender].
     */
    private fun sendToOrchestrator(message: Message<OrchestratorMessage>) {
        log.info(
            "Sending '${message.payload::class.simpleName}' message " +
                    "to '${OrchestratorEndpoint::class.simpleName}'. " +
                    "TraceID: '${message.header.traceId}'."
        )

        sender.send(message)
    }
}
