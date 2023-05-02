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
import io.kotest.extensions.system.OverrideMode
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContainAll
import io.kotest.matchers.maps.shouldContainAll
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
        val client = mockk<BatchV1Api> {
            every { createNamespacedJob(any(), any(), null, null, null, null) } returns mockk()
        }

        val traceId = "0123456789".repeat(20)
        val payload = AnalyzerRequest(1)
        val header = MessageHeader(token = "testToken", traceId = traceId)
        val message = Message(header, payload)

        val commands = listOf("/bin/sh")
        val arguments = listOf("-c", "exec java -cp @/app/jib-classpath-file @/app/jib-main-class-file")
        val envVars = mapOf(
            "SPECIFIC_PROPERTY" to "bar",
            "SHELL" to "/bin/bash",
            "token" to header.token,
            "traceId" to header.traceId,
            "payload" to "{\"analyzerJobId\":${payload.analyzerJobId}}",
            "ANALYZER_SPECIFIC_PROPERTY" to "foo"
        )

        val expectedEnvVars = envVars.toMutableMap()
        expectedEnvVars["SPECIFIC_PROPERTY"] = "foo"
        expectedEnvVars -= "ANALYZER_SPECIFIC_PROPERTY"

        val config = KubernetesSenderConfig(
            namespace = "test-namespace",
            imageName = "busybox",
            commands = commands,
            args = arguments,
            imagePullPolicy = "Always",
            restartPolicy = "Never",
            backoffLimit = 11,
            imagePullSecret = "image_pull_secret",
            secretVolumes = listOf(
                SecretVolumeMount("secretService", "/mnt/secret"),
                SecretVolumeMount("topSecret", "/mnt/top/secret")
            )
        )

        val sender = KubernetesMessageSender(
            api = client,
            config = config,
            endpoint = AnalyzerEndpoint
        )

        withEnvironment(envVars, OverrideMode.SetOrOverride) {
            sender.send(message)
        }

        val job = slot<V1Job>()
        verify(exactly = 1) {
            client.createNamespacedJob(
                "test-namespace",
                capture(job),
                null,
                null,
                null,
                null
            )
        }

        job.captured.spec?.template?.spec?.containers?.single().shouldNotBeNull {
            image shouldBe config.imageName
            imagePullPolicy shouldBe config.imagePullPolicy
            command shouldBe config.commands
            args shouldBe config.args
            val jobEnvironment = env!!.associate { it.name to it.value }
            jobEnvironment shouldContainAll expectedEnvVars
            jobEnvironment.keys shouldNotContainAll listOf("_", "HOME", "PATH", "PWD")

            val mounts = volumeMounts.orEmpty()
            mounts shouldHaveSize 2
            with(mounts[0]) {
                readOnly shouldBe true
                name shouldBe "secret-volume-1"
                mountPath shouldBe "/mnt/secret"
            }
            with(mounts[1]) {
                readOnly shouldBe true
                name shouldBe "secret-volume-2"
                mountPath shouldBe "/mnt/top/secret"
            }
        }

        job.captured.spec?.backoffLimit shouldBe config.backoffLimit
        job.captured.spec?.template?.spec?.restartPolicy shouldBe config.restartPolicy
        job.captured.spec?.template?.spec?.imagePullSecrets.orEmpty()
            .map { it.name } shouldContainOnly listOf(config.imagePullSecret)

        val volumes = job.captured.spec?.template?.spec?.volumes.orEmpty()
        volumes shouldHaveSize 2
        volumes.map { it.name } shouldContainExactly listOf("secret-volume-1", "secret-volume-2")
        val secrets = volumes.mapNotNull { it.secret?.secretName }
        secrets shouldContainExactly listOf("secretService", "topSecret")

        val labels = job.captured.metadata?.labels.orEmpty()
        val traceIdFromLabels = (0..3).fold("") { id, idx ->
            id + labels.getValue("trace-id-$idx")
        }
        traceIdFromLabels shouldBe traceId
    }
})
