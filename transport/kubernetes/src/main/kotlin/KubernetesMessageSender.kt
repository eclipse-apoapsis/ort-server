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

    /** The namespace inside the Kubernetes Cluster to interact with. */
    val namespace: String,

    /** The image name for the container. */
    val imageName: String,

    /** A list of commands for the container that runs in the Pod. **/
    val commands: List<String>,

    /** A map for environment variables, which will be set for the container that runs in the Pod. **/
    val envVars: Map<String, String>,

    endpoint: Endpoint<T>
) : MessageSender<T> {
    /** The object to serialize the payload of messages. */
    private val serializer = JsonSerializer.forClass(endpoint.messageClass)

    override fun send(message: Message<T>) {
        val msgMap = mapOf(
            "token" to message.header.token,
            "traceId" to message.header.traceId,
            "payload" to serializer.toJson(message.payload)
        )

        // TODO: Certain parameters like e.g. restartPolicy, backoffLimit are currently hard-coded and may be made
        //       configurable in the future.
        val jobBody = V1JobBuilder()
            .withNewMetadata()
            .withName("job-$imageName-${message.header.traceId}")
            .endMetadata()
            .withNewSpec()
            .withBackoffLimit(2)
            .withNewTemplate()
            .withNewSpec()
            .withRestartPolicy("Always")
            .addNewContainer()
            .withName("pod-$imageName-${message.header.traceId}")
            .withImage(imageName)
            .withCommand(commands)
            .withImagePullPolicy("Never")
            .withEnv((envVars + msgMap).map { V1EnvVarBuilder().withName(it.key).withValue(it.value).build() })
            .endContainer()
            .withRestartPolicy("OnFailure")
            .endSpec()
            .endTemplate()
            .endSpec()
            .build()

        api.createNamespacedJob(namespace, jobBody, null, null, null, null)
    }
}
