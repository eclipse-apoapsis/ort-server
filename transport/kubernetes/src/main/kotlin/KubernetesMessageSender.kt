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

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.models.V1EnvVarBuilder
import io.kubernetes.client.openapi.models.V1JobBuilder
import io.kubernetes.client.openapi.models.V1LocalObjectReference

import org.ossreviewtoolkit.server.transport.Endpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageSender
import org.ossreviewtoolkit.server.transport.json.JsonSerializer

/**
 * Implementation of the [MessageSender] interface for Kubernetes.
 */
internal class KubernetesMessageSender<T : Any>(
    /** The Kubernetes API for creating jobs. */
    val api: BatchV1Api,

    /** The configuration defining the job to be created. */
    val config: KubernetesConfig,

    /** Determines the target endpoint. */
    val endpoint: Endpoint<T>
) : MessageSender<T> {
    /** The object to serialize the payload of messages. */
    private val serializer = JsonSerializer.forClass(endpoint.messageClass)

    override fun send(message: Message<T>) {
        val msgMap = mapOf(
            "token" to message.header.token,
            "traceId" to message.header.traceId,
            "payload" to serializer.toJson(message.payload)
        )

        val envVars = System.getenv()

        val jobBody = V1JobBuilder()
            .withNewMetadata()
            .withName("${endpoint.configPrefix}-${message.header.traceId}".take(64))
            .endMetadata()
            .withNewSpec()
            .withBackoffLimit(config.backoffLimit)
            .withNewTemplate()
            .withNewSpec()
            .withRestartPolicy(config.restartPolicy)
            .withImagePullSecrets(
                listOfNotNull(config.imagePullSecret).map { V1LocalObjectReference().name(it) }
            )
            .addNewContainer()
            .withName("${endpoint.configPrefix}-${message.header.traceId}".take(64))
            .withImage(config.imageName)
            .withCommand(config.commands)
            .withImagePullPolicy(config.imagePullPolicy)
            .withEnv((envVars + msgMap).map { V1EnvVarBuilder().withName(it.key).withValue(it.value).build() })
            .endContainer()
            .endSpec()
            .endTemplate()
            .endSpec()
            .build()

        api.createNamespacedJob(config.namespace, jobBody, null, null, null, null)
    }
}
