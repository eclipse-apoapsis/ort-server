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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

import java.nio.file.Paths

import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.MessageSenderFactory

private const val NAMESPACE = "test-namespace"
private const val IMAGE_NAME = "busybox"
private const val RESTART_POLICY = "sometimes"
private const val IMAGE_PULL_POLICY = "frequently"
private const val IMAGE_PULL_SECRET = "image_pull_secret"
private const val BACKOFF_LIMIT = 42
private const val COMMANDS = "foo bar \"hello world\" baz"

class KubernetesMessageSenderFactoryTest : StringSpec({
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
            "$keyPrefix.backoffLimit" to BACKOFF_LIMIT,
            "$keyPrefix.enableDebugLogging" to "true"
        )
        val config = ConfigFactory.parseMap(configMap)

        val kubeconfigPath = Paths.get(this.javaClass.getResource("/kubeconfig")!!.toURI()).toFile().absolutePath

        val sender = withEnvironment("KUBECONFIG" to kubeconfigPath) {
            MessageSenderFactory.createSender(AnalyzerEndpoint, config)
        }

        sender.shouldBeTypeOf<KubernetesMessageSender<AnalyzerEndpoint>>()
        with(sender.config) {
            namespace shouldBe NAMESPACE
            imageName shouldBe IMAGE_NAME
            imagePullPolicy shouldBe IMAGE_PULL_POLICY
            imagePullSecret shouldBe IMAGE_PULL_SECRET
            commands shouldContainInOrder listOf("foo", "bar", "hello world", "baz")
            backoffLimit shouldBe BACKOFF_LIMIT
            restartPolicy shouldBe RESTART_POLICY
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
        val config = ConfigFactory.parseMap(configMap)

        val kubeconfigPath = Paths.get(this.javaClass.getResource("/kubeconfig")!!.toURI()).toFile().absolutePath

        val sender = withEnvironment("KUBECONFIG" to kubeconfigPath) {
            MessageSenderFactory.createSender(AnalyzerEndpoint, config)
        }

        sender.shouldBeTypeOf<KubernetesMessageSender<AnalyzerEndpoint>>()
        with(sender.config) {
            commands should beEmpty()
            backoffLimit shouldBe 2
            imagePullPolicy shouldBe "Never"
            restartPolicy shouldBe "OnFailure"
            imagePullSecret should beNull()
            enableDebugLogging shouldBe false
        }

        sender.api.apiClient.isDebugging shouldBe false
    }
})
