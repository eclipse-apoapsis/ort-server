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

package org.ossreviewtoolkit.server.transport.kubernetes

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.models.V1Job

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerRequest
import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class KubernetesMessageSenderTest : StringSpec({
    "Kubernetes jobs are created via the sender" {
        val client = mockk<BatchV1Api>()
        every { client.createNamespacedJob(any(), any(), null, null, null, null) } returns mockk()

        val payload = AnalyzerRequest(1)
        val header = MessageHeader(token = "testToken", traceId = "testTraceId")
        val message = Message(header, payload)

        val commands = listOf("/bin/echo", "Hello World")
        val envVars = mapOf(
            "SHELL" to "/bin/bash",
            "token" to header.token,
            "traceId" to header.traceId,
            "payload" to "{\"analyzerJobId\":${payload.analyzerJobId}}"
        )
        val namespace = "test-namespace"
        val imageName = "busybox"

        val sender = KubernetesMessageSender(
            api = client,
            namespace = namespace,
            imageName = imageName,
            commands = commands,
            envVars = envVars,
            endpoint = AnalyzerEndpoint
        )

        sender.send(message)

        val job = slot<V1Job>()
        verify(exactly = 1) {
            client.createNamespacedJob(
                namespace,
                capture(job),
                null,
                null,
                null,
                null
            )
        }

        job.captured.spec?.template?.spec?.containers?.single().shouldNotBeNull {
            image shouldBe imageName
            command shouldBe commands
            env?.associate { it.name to it.value } shouldBe envVars
        }
    }
})
