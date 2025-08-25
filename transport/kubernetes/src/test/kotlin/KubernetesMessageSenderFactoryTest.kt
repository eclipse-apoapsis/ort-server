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
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.MessageSenderFactory
import org.eclipse.apoapsis.ortserver.utils.test.withEnvironment

private const val NAMESPACE = "test-namespace"
private const val IMAGE_NAME = "busybox"
private const val RESTART_POLICY = "sometimes"
private const val IMAGE_PULL_POLICY = "frequently"
private const val IMAGE_PULL_SECRET = "image_pull_secret"
private const val BACKOFF_LIMIT = 42
private const val COMMANDS = "foo bar \"hello world\" baz"
private const val ARGS = "run \"all tests\" fast"
private const val SECRET_MOUNTS =
    "secret1->/mnt/sec1|sub1 \"secret2->/path/with/white space\" \"secret3 -> /mnt/other | sub2\""
private const val PVC_MOUNTS = "pvc1->/mnt/pvc1,R \"pvc2->/path/with/white space,W\" \"pvc3 -> /mnt/other,r\""
private const val SERVICE_ACCOUNT = "test_service_account"

private val annotationVariables = mapOf(
    "TEST_ANNOTATION" to "ort-server.org/test=true",
    "SPEED_ANNOTATION" to "ort-server.org/performance = fast"
)

class KubernetesMessageSenderFactoryTest : StringSpec({
    val kubeconfigPath = tempfile().apply {
        writeText(KubernetesMessageSenderFactoryTest::class.java.getResource("/kubeconfig").readText())
    }.absolutePath

    "A correct MessageSender can be created" {
        val keyPrefix = "analyzer.sender"
        val configMap = mapOf(
            "$keyPrefix.type" to KubernetesSenderConfig.TRANSPORT_NAME,
            "$keyPrefix.namespace" to NAMESPACE,
            "$keyPrefix.imageName" to IMAGE_NAME,
            "$keyPrefix.restartPolicy" to RESTART_POLICY,
            "$keyPrefix.imagePullPolicy" to IMAGE_PULL_POLICY,
            "$keyPrefix.imagePullSecret" to IMAGE_PULL_SECRET,
            "$keyPrefix.commands" to COMMANDS,
            "$keyPrefix.args" to ARGS,
            "$keyPrefix.backoffLimit" to BACKOFF_LIMIT,
            "$keyPrefix.enableDebugLogging" to "true",
            "$keyPrefix.mountSecrets" to SECRET_MOUNTS,
            "$keyPrefix.mountPvcs" to PVC_MOUNTS,
            "$keyPrefix.annotationVariables" to annotationVariables.keys.joinToString(),
            "$keyPrefix.serviceAccount" to SERVICE_ACCOUNT,
        )
        val configManager = ConfigManager.create(ConfigFactory.parseMap(configMap))

        val envVariables = annotationVariables + ("KUBECONFIG" to kubeconfigPath)
        val sender = withEnvironment(envVariables) {
            MessageSenderFactory.createSender(AnalyzerEndpoint, configManager)
        }

        sender.shouldBeTypeOf<KubernetesMessageSender<AnalyzerEndpoint>>()
        with(sender.config) {
            namespace shouldBe NAMESPACE
            imageName shouldBe IMAGE_NAME
            imagePullPolicy shouldBe IMAGE_PULL_POLICY
            imagePullSecret shouldBe IMAGE_PULL_SECRET
            commands shouldContainInOrder listOf("foo", "bar", "hello world", "baz")
            args shouldContainInOrder listOf("run", "all tests", "fast")
            backoffLimit shouldBe BACKOFF_LIMIT
            restartPolicy shouldBe RESTART_POLICY
            secretVolumes shouldContainInOrder listOf(
                SecretVolumeMount("secret1", "/mnt/sec1", "sub1"),
                SecretVolumeMount("secret2", "/path/with/white space"),
                SecretVolumeMount("secret3", "/mnt/other", "sub2")
            )
            pvcVolumes shouldContainInOrder listOf(
                PvcVolumeMount("pvc1", "/mnt/pvc1", readOnly = true),
                PvcVolumeMount("pvc2", "/path/with/white space", readOnly = false),
                PvcVolumeMount("pvc3", "/mnt/other", readOnly = true)
            )
            annotations shouldContainExactly mapOf(
                "ort-server.org/test" to "true",
                "ort-server.org/performance" to "fast"
            )
            serviceAccountName shouldBe SERVICE_ACCOUNT
            enableDebugLogging shouldBe true
        }

        sender.api.apiClient.isDebugging shouldBe true
    }

    "A correct MessageSender can be created with default configuration settings" {
        val keyPrefix = "analyzer.sender"
        val configMap = mapOf(
            "$keyPrefix.type" to KubernetesSenderConfig.TRANSPORT_NAME,
            "$keyPrefix.namespace" to NAMESPACE,
            "$keyPrefix.imageName" to IMAGE_NAME
        )
        val configManager = ConfigManager.create(ConfigFactory.parseMap(configMap))

        val sender = withEnvironment("KUBECONFIG" to kubeconfigPath) {
            MessageSenderFactory.createSender(AnalyzerEndpoint, configManager)
        }

        sender.shouldBeTypeOf<KubernetesMessageSender<AnalyzerEndpoint>>()
        with(sender.config) {
            commands should beEmpty()
            args should beEmpty()
            backoffLimit shouldBe 2
            imagePullPolicy shouldBe "Never"
            restartPolicy shouldBe "OnFailure"
            imagePullSecret should beNull()
            secretVolumes should beEmpty()
            annotations.keys should beEmpty()
            serviceAccountName should beNull()
            enableDebugLogging shouldBe false
        }

        sender.api.apiClient.isDebugging shouldBe false
    }

    "Invalid secret mount declarations are ignored" {
        val keyPrefix = "analyzer.sender"
        val configMap = mapOf(
            "$keyPrefix.type" to KubernetesSenderConfig.TRANSPORT_NAME,
            "$keyPrefix.namespace" to NAMESPACE,
            "$keyPrefix.imageName" to IMAGE_NAME,
            "$keyPrefix.mountSecrets" to "$SECRET_MOUNTS plus invalid secret mounts->"
        )
        val configManager = ConfigManager.create(ConfigFactory.parseMap(configMap))

        val sender = MessageSenderFactory.createSender(AnalyzerEndpoint, configManager)

        sender.shouldBeTypeOf<KubernetesMessageSender<AnalyzerEndpoint>>()
        sender.config.secretVolumes shouldContainInOrder listOf(
            SecretVolumeMount("secret1", "/mnt/sec1", "sub1"),
            SecretVolumeMount("secret2", "/path/with/white space"),
            SecretVolumeMount("secret3", "/mnt/other", "sub2")
        )
    }

    "Invalid pvc mount declarations are ignored" {
        val keyPrefix = "analyzer.sender"
        val configMap = mapOf(
            "$keyPrefix.type" to KubernetesSenderConfig.TRANSPORT_NAME,
            "$keyPrefix.namespace" to NAMESPACE,
            "$keyPrefix.imageName" to IMAGE_NAME,
            "$keyPrefix.mountPvcs" to "$PVC_MOUNTS p->/foo,X plus invalid secret mounts->"
        )
        val configManager = ConfigManager.create(ConfigFactory.parseMap(configMap))

        val sender = MessageSenderFactory.createSender(AnalyzerEndpoint, configManager)

        sender.shouldBeTypeOf<KubernetesMessageSender<AnalyzerEndpoint>>()

        sender.config.pvcVolumes shouldContainInOrder listOf(
            PvcVolumeMount("pvc1", "/mnt/pvc1", readOnly = true),
            PvcVolumeMount("pvc2", "/path/with/white space", readOnly = false),
            PvcVolumeMount("pvc3", "/mnt/other", readOnly = true)
        )
    }

    "Invalid variables defining annotations are ignored" {
        val keyPrefix = "analyzer.sender"
        val validVariable = "validVariable"
        val invalidVariable = "invalidVariable"
        val configMap = mapOf(
            "$keyPrefix.type" to KubernetesSenderConfig.TRANSPORT_NAME,
            "$keyPrefix.namespace" to NAMESPACE,
            "$keyPrefix.imageName" to IMAGE_NAME,
            "$keyPrefix.annotationVariables" to "$invalidVariable,$validVariable"
        )
        val configManager = ConfigManager.create(ConfigFactory.parseMap(configMap))

        val environment = mapOf(
            validVariable to "foo=bar",
            invalidVariable to "not a valid annotation"
        )
        withEnvironment(environment) {
            val sender = MessageSenderFactory.createSender(AnalyzerEndpoint, configManager)

            sender.shouldBeTypeOf<KubernetesMessageSender<AnalyzerEndpoint>>()
            sender.config.annotations shouldContainExactly mapOf("foo" to "bar")
        }
    }

    "Non-existing variables defining annotations are ignored" {
        val keyPrefix = "analyzer.sender"
        val validVariable = "validVariable"
        val nonExistingVariable = "nonExistingVariable"
        val configMap = mapOf(
            "$keyPrefix.type" to KubernetesSenderConfig.TRANSPORT_NAME,
            "$keyPrefix.namespace" to NAMESPACE,
            "$keyPrefix.imageName" to IMAGE_NAME,
            "$keyPrefix.annotationVariables" to "$nonExistingVariable,$validVariable"
        )
        val configManager = ConfigManager.create(ConfigFactory.parseMap(configMap))

        val environment = mapOf(
            validVariable to "foo=bar"
        )
        withEnvironment(environment) {
            val sender = MessageSenderFactory.createSender(AnalyzerEndpoint, configManager)

            sender.shouldBeTypeOf<KubernetesMessageSender<AnalyzerEndpoint>>()
            sender.config.annotations shouldContainExactly mapOf("foo" to "bar")
        }
    }
})
