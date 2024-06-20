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

package org.eclipse.apoapsis.ortserver.transport.kubernetes

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.system.OverrideMode
import io.kotest.extensions.system.withEnvironment
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.models.V1Job

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.RUN_ID_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.TRACE_PROPERTY

class KubernetesMessageSenderTest : StringSpec({
    "Kubernetes jobs are created via the sender" {
        val expectedEnvVars = envVars.toMutableMap()
        expectedEnvVars["SPECIFIC_PROPERTY"] = "foo"
        expectedEnvVars[RUN_ID_PROPERTY] = message.header.ortRunId.toString()
        expectedEnvVars -= "ANALYZER_SPECIFIC_PROPERTY"
        val senderConfig = createConfig()

        val job = createJob(senderConfig)

        job.spec?.template?.spec?.containers?.single().shouldNotBeNull {
            image shouldBe senderConfig.imageName
            imagePullPolicy shouldBe senderConfig.imagePullPolicy
            command shouldBe senderConfig.commands
            args shouldBe senderConfig.args
            resources should beNull()
            val jobEnvironment = env!!.associate { it.name to it.value }
            jobEnvironment shouldContainAll expectedEnvVars
            jobEnvironment.keys shouldNotContainAll listOf("_", "HOME", "PATH", "PWD")

            val mounts = volumeMounts.orEmpty()
            mounts shouldHaveSize 4
            with(mounts[0]) {
                readOnly shouldBe true
                name shouldBe "secret-volume-1"
                mountPath shouldBe "/mnt/secret"
                subPath should beNull()
            }
            with(mounts[1]) {
                readOnly shouldBe true
                name shouldBe "secret-volume-2"
                mountPath shouldBe "/mnt/top/secret"
                subPath shouldBe "sub-secret"
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

    "Resource definitions should be supported that are customizable based on message properties" {
        val config = createConfig(
            "cpuLimit" to "\${cpuLimitVar}",
            "cpuRequest" to "\${cpuRequestVar}",
            "memoryLimit" to "\${memoryLimitVar}",
            "memoryRequest" to "\${memoryRequestVar}"
        )
        val msg = messageWithProperties(
            "kubernetes.cpuLimitVar" to "500m",
            "kubernetes.cpuRequestVar" to "250m",
            "kubernetes.memoryLimitVar" to "1000Mi",
            "kubernetes.memoryRequestVar" to "512Mi"
        )

        val job = createJob(config, msg)

        job.spec?.template?.spec?.containers?.single()?.resources.shouldNotBeNull {
            val expectedLimits = mapOf(
                "cpu" to Quantity("500m"),
                "memory" to Quantity("1000Mi")
            )
            limits shouldBe expectedLimits

            val expectedRequests = mapOf(
                "cpu" to Quantity("250m"),
                "memory" to Quantity("512Mi")
            )
            requests shouldBe expectedRequests
        }
    }

    "Valid job names are generated even if no trace ID is provided" {
        val config = createConfig()
        val msg = message.copy(header = header.copy(traceId = " "))

        val (client, sender) = createClientAndSender(config)

        val jobs = mutableListOf<V1Job>()
        sender.send(msg)
        sender.send(msg)

        verify(exactly = 2) {
            client.createNamespacedJob("test-namespace", capture(jobs))
        }

        val jobNames = jobs.mapNotNull { it.metadata?.name }.toSet()
        jobNames shouldHaveSize 2
        jobNames.forAll { jobName ->
            jobName.shouldStartWith("analyzer-")
            jobName.length shouldBeGreaterThan 20
        }
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
private val header = MessageHeader(traceId = traceId, 9)
private val message = Message(header, payload)

private val envVars = mapOf(
    "SPECIFIC_PROPERTY" to "bar",
    "SHELL" to "/bin/bash",
    TRACE_PROPERTY to header.traceId,
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
    val (client, sender) = createClientAndSender(config)

    withEnvironment(envVars, OverrideMode.SetOrOverride) {
        sender.send(msg)
    }

    val job = slot<V1Job>()
    verify(exactly = 1) {
        client.createNamespacedJob("test-namespace", capture(job))
    }

    return job.captured
}

/**
 * Create a sender to be tested based on the given [config] together with a mocked Kubernetes client.
 */
private fun createClientAndSender(
    config: KubernetesSenderConfig
): Pair<BatchV1Api, KubernetesMessageSender<AnalyzerRequest>> {
    val request = mockk<BatchV1Api.APIcreateNamespacedJobRequest> {
        every { execute() } returns mockk()
    }

    val client = mockk<BatchV1Api> {
        every { createNamespacedJob(any(), any()) } returns request
    }

    val sender = KubernetesMessageSender(
        api = client,
        config = config,
        endpoint = AnalyzerEndpoint
    )
    return Pair(client, sender)
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
        "mountSecrets" to "secretService->/mnt/secret topSecret->/mnt/top/secret|sub-secret",
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
    actualLabels["ort-worker"] shouldBe "analyzer"
}
