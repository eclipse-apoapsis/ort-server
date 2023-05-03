/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.transport.kubernetes.jobmonitor

import io.kubernetes.client.openapi.models.V1Job

import org.ossreviewtoolkit.server.model.orchestrator.OrchestratorMessage
import org.ossreviewtoolkit.server.model.orchestrator.WorkerError
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessageSender

/** A prefix for the name of a label storing a part of the trace ID. */
private const val TRACE_LABEL_PREFIX = "trace-id-"

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
            val traceLabels = job.metadata?.labels.orEmpty().filterKeys { it.startsWith(TRACE_LABEL_PREFIX) }.toList()
                .sortedBy { it.first.substringAfter(TRACE_LABEL_PREFIX).toInt() }
            val traceId = traceLabels.fold("") { id, label -> "$id${label.second}" }

            if (traceId.isNotEmpty()) {
                val header = MessageHeader(token = "", traceId = traceId)
                val message = Message(header, WorkerError(endpointName))
                sender.send(message)
            }
        }
    }
}
