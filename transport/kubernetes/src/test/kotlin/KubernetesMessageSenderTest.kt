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
        val expectedEnvVars = envVars.toMutableMap()
        expectedEnvVars["SPECIFIC_PROPERTY"] = "foo"
        expectedEnvVars["runId"] = message.header.ortRunId.toString()
        expectedEnvVars -= "ANALYZER_SPECIFIC_PROPERTY"
        val senderConfig = createConfig()

        val job = createJob(senderConfig)

        job.spec?.template?.spec?.containers?.single().shouldNotBeNull {
            image shouldBe senderConfig.imageName
            imagePullPolicy shouldBe senderConfig.imagePullPolicy
            command shouldBe senderConfig.commands
            args shouldBe senderConfig.args
            val jobEnvironment = env!!.associate { it.name to it.value }
            jobEnvironment shouldContainAll expectedEnvVars
            jobEnvironment.keys shouldNotContainAll listOf("_", "HOME", "PATH", "PWD")

            val mounts = volumeMounts.orEmpty()
            mounts shouldHaveSize 4
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
            with(mounts[2]) {
                readOnly shouldBe true
                name shouldBe "pvc-volume-1"
                mountPath shouldBe "/mnt/readOnly"
            }
            with(mounts[3]) {
                readOnly shouldBe false
                name shouldBe "pvc-volume-2"
                mountPath shouldBe "/mnt/data"
            }
        }

        job.spec?.backoffLimit shouldBe senderConfig.backoffLimit
        job.spec?.template?.spec?.securityContext?.runAsUser shouldBe senderConfig.userId
        job.spec?.template?.spec?.restartPolicy shouldBe senderConfig.restartPolicy
        job.spec?.template?.spec?.serviceAccountName shouldBe senderConfig.serviceAccountName
        job.spec?.template?.spec?.imagePullSecrets.orEmpty()
            .map { it.name } shouldContainOnly listOf(senderConfig.imagePullSecret)

        val volumes = job.spec?.template?.spec?.volumes.orEmpty()
        volumes shouldHaveSize 4
        volumes.map { it.name } shouldContainExactly listOf(
            "secret-volume-1",
            "secret-volume-2",
            "pvc-volume-1",
            "pvc-volume-2"
        )
        val secrets = volumes.mapNotNull { it.secret?.secretName }
        secrets shouldContainExactly listOf("secretService", "topSecret")

        verifyLabels(
            actualLabels = job.metadata?.labels.orEmpty(),
            expectedRunId = message.header.ortRunId
        )

        val jobAnnotations = job.spec?.template?.metadata?.annotations.orEmpty()
        jobAnnotations shouldBe annotations

        verifyLabels(
            actualLabels = job.spec?.template?.metadata?.labels.orEmpty(),
            expectedRunId = message.header.ortRunId
        )
    }

    "The container image should be customizable based on message properties" {
        val jdkVariable = "javaVersion"
        val config = createConfig("imageName" to "analyzer-\${$jdkVariable}")
        val msg = messageWithProperties("kubernetes.$jdkVariable" to "18")

        val job = createJob(config, msg)

        job.spec?.template?.spec?.containers?.single()?.image shouldBe "analyzer-18"
    }
})

private val annotations = mapOf(
    "test.annotation1" to "a test annotation",
    "test.annotation2" to "anotherTestAnnotation"
)

private val annotationVariables = mapOf(
    "v1" to "test.annotation1=a test annotation",
    "v2" to "test.annotation2=anotherTestAnnotation"
)

private val traceId = "0123456789".repeat(20)
private val payload = AnalyzerRequest(1)
private val header = MessageHeader(token = "testToken", traceId = traceId, 9)
private val message = Message(header, payload)

private val envVars = mapOf(
    "SPECIFIC_PROPERTY" to "bar",
    "SHELL" to "/bin/bash",
    "token" to header.token,
    "traceId" to header.traceId,
    "payload" to "{\"analyzerJobId\":${payload.analyzerJobId}}",
    "ANALYZER_SPECIFIC_PROPERTY" to "foo"
)

/**
 * Invoke a [KubernetesMessageSender] test instance to create a job based on the given [config] and [msg]. Return
 * the resulting [V1Job].
 */
private fun createJob(
    config: KubernetesSenderConfig,
    msg: Message<AnalyzerRequest> = message
): V1Job {
    val client = mockk<BatchV1Api> {
        every { createNamespacedJob(any(), any(), null, null, null, null) } returns mockk()
    }

    val sender = KubernetesMessageSender(
        api = client,
        config = config,
        endpoint = AnalyzerEndpoint
    )

    withEnvironment(envVars, OverrideMode.SetOrOverride) {
        sender.send(msg)
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

    return job.captured
}

/**
 * Create a [KubernetesSenderConfig] with default properties that can be overridden with the given [overrides].
 */
private fun createConfig(vararg overrides: Pair<String, String>): KubernetesSenderConfig {
    val defaultProperties = mapOf(
        "namespace" to "test-namespace",
        "imageName" to "busybox",
        "commands" to "/bin/sh",
        "args" to "-c exec java -cp @/app/jib-classpath-file @/app/jib-main-class-file",
        "imagePullPolicy" to "Always",
        "userId" to 1111L,
        "restartPolicy" to "Never",
        "backoffLimit" to 11,
        "imagePullSecret" to "image_pull_secret",
        "mountSecrets" to "secretService->/mnt/secret topSecret->/mnt/top/secret",
        "mountPvcs" to "pvc1->/mnt/readOnly,R pvc2->/mnt/data,W",
        "annotationVariables" to "v1,v2",
        "serviceAccountName" to "test_service_account"
    )
    val overrideProperties = mapOf(*overrides)

    return withEnvironment(annotationVariables) {
        val config = ConfigFactory.parseMap(overrideProperties).withFallback(ConfigFactory.parseMap(defaultProperties))

        KubernetesSenderConfig.createConfig(config)
    }
}

/**
 * Return a copy of the test message that contains the given [properties].
 */
private fun messageWithProperties(vararg properties: Pair<String, String>): Message<AnalyzerRequest> =
    message.copy(header = header.copy(transportProperties = mapOf(*properties)))

private fun verifyLabels(actualLabels: Map<String, String>, expectedRunId: Long) {
    val traceIdFromLabels = (0..3).fold("") { id, idx ->
        id + actualLabels.getValue("trace-id-$idx")
    }
    traceIdFromLabels shouldBe traceId

    actualLabels["run-id"] shouldBe expectedRunId.toString()
}
