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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.MessageSenderFactory

private const val NAMESPACE = "test-namespace"
private const val IMAGENAME = "busybox"

class KubernetesMessageSenderFactoryTest : StringSpec({
    "A correct MessageSender can be created" {
        val keyPrefix = "analyzer.sender"
        val configMap = mapOf(
            "$keyPrefix.type" to KubernetesConfig.TRANSPORT_NAME,
            "$keyPrefix.namespace" to NAMESPACE,
            "$keyPrefix.imageName" to IMAGENAME
        )
        val config = ConfigFactory.parseMap(configMap)

        val sender = MessageSenderFactory.createSender(AnalyzerEndpoint, config)

        sender.shouldBeTypeOf<KubernetesMessageSender<AnalyzerEndpoint>>()
        sender.namespace shouldBe NAMESPACE
        sender.imageName shouldBe IMAGENAME
        sender.commands shouldBe emptyList()
        sender.envVars shouldBe System.getenv()
    }
})
