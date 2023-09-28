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

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerRequest
import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class KubernetesMessageReceiverFactoryTest : StringSpec({
    beforeAny {
        mockkObject(KubernetesMessageReceiverFactory)
        every { KubernetesMessageReceiverFactory.exit(any()) } just runs
    }

    afterAny {
        unmockkAll()
    }

    "Messages can be received via the Kubernetes transport" {
        val payload = AnalyzerRequest(1)
        val header = MessageHeader(token = "testToken", traceId = "testTraceId", ortRunId = 33)

        val env = mapOf(
            "token" to header.token,
            "traceId" to header.traceId,
            "runId" to header.ortRunId.toString(),
            "payload" to "{\"analyzerJobId\":${payload.analyzerJobId}}"
        )

        withEnvironment(env) {
            val configMap = mapOf(
                "type" to KubernetesSenderConfig.TRANSPORT_NAME,
                "namespace" to "test-namespace",
                "imageName" to "busybox",
                ConfigManager.CONFIG_MANAGER_SECTION to mapOf("someProperty" to "someValue")
            )

            val configManager = ConfigManager.create(ConfigFactory.parseMap(configMap))

            var receivedMessage: Message<AnalyzerRequest>? = null
            KubernetesMessageReceiverFactory().createReceiver(AnalyzerEndpoint, configManager) { message ->
                receivedMessage = message
            }

            receivedMessage.shouldNotBeNull {
                this.header.token shouldBe header.token
                this.header.traceId shouldBe header.traceId
                this.header.ortRunId shouldBe header.ortRunId
                this.payload shouldBe payload
            }

            verify {
                KubernetesMessageReceiverFactory.exit(0)
            }
        }
    }

    "The process is terminated even if an exception is thrown by the handler" {
        val payload = AnalyzerRequest(1)
        val header = MessageHeader(token = "testToken", traceId = "testTraceId", ortRunId = 7)

        val env = mapOf(
            "token" to header.token,
            "traceId" to header.traceId,
            "runId" to header.ortRunId.toString(),
            "payload" to "{\"analyzerJobId\":${payload.analyzerJobId}}"
        )

        withEnvironment(env) {
            val configMap = mapOf(
                "type" to KubernetesSenderConfig.TRANSPORT_NAME,
                "namespace" to "test-namespace",
                "imageName" to "busybox",
                ConfigManager.CONFIG_MANAGER_SECTION to mapOf("someProperty" to "someValue")
            )
            val configManager = ConfigManager.create(ConfigFactory.parseMap(configMap))

            KubernetesMessageReceiverFactory().createReceiver(AnalyzerEndpoint, configManager) { _ ->
                throw IllegalStateException("Test exception")
            }

            verify {
                KubernetesMessageReceiverFactory.exit(1)
            }
        }
    }
})
