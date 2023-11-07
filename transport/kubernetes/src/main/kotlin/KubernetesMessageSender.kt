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
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource
import io.kubernetes.client.openapi.models.V1PodSecurityContextBuilder
import io.kubernetes.client.openapi.models.V1SecretVolumeSource
import io.kubernetes.client.openapi.models.V1Volume
import io.kubernetes.client.openapi.models.V1VolumeMount

import org.ossreviewtoolkit.server.transport.Endpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageSender
import org.ossreviewtoolkit.server.transport.json.JsonSerializer

/** A prefix for the name of a label storing a part of the trace ID. */
private const val TRACE_LABEL_PREFIX = "trace-id-"

/** The maximum length of the value of a trace label. */
private const val TRACE_LABEL_LENGTH = 60

/** The label to store the current ORT run ID. */
private const val RUN_ID_LABEL = "run-id"

/** A prefix for generating names for secret volumes. */
private const val SECRET_VOLUME_PREFIX = "secret-volume-"

/** A prefix for generating names for PVC volumes. */
private const val PVC_VOLUME_PREFIX = "pvc-volume-"

/**
 * A set with the names of environment variables that should not be passed to the environment of newly created jobs.
 * These variables have a special meaning and make no sense or can even cause problems in the context of another pod.
 */
private val VARIABLES_NOT_TO_PROPAGATE = setOf("_", "HOME", "PATH", "PWD")

/**
 * Implementation of the [MessageSender] interface for Kubernetes.
 */
internal class KubernetesMessageSender<T : Any>(
    /** The Kubernetes API for creating jobs. */
    val api: BatchV1Api,

    /** The configuration defining the job to be created. */
    val config: KubernetesSenderConfig,

    /** Determines the target endpoint. */
    val endpoint: Endpoint<T>
) : MessageSender<T> {
    /** The object to serialize the payload of messages. */
    private val serializer = JsonSerializer.forClass(endpoint.messageClass)

    override fun send(message: Message<T>) {
        val msgMap = mapOf(
            "token" to message.header.token,
            "traceId" to message.header.traceId,
            "runId" to message.header.ortRunId.toString(),
            "payload" to serializer.toJson(message.payload)
        )

        val envVars = createEnvironment()
        val labels = createTraceIdLabels(message.header.traceId) + (RUN_ID_LABEL to message.header.ortRunId.toString())

        val jobBody = V1JobBuilder()
            .withNewMetadata()
                .withName("${endpoint.configPrefix}-${message.header.traceId}".take(64))
                .withLabels<String, String>(labels)
            .endMetadata()
            .withNewSpec()
                .withBackoffLimit(config.backoffLimit)
                .withNewTemplate()
                    .withNewMetadata()
                        .withAnnotations<String, String>(config.annotations)
                        .withLabels<String, String>(labels)
                    .endMetadata()
                    .withNewSpec()
                        .withSecurityContext(V1PodSecurityContextBuilder().withRunAsUser(config.userId).build())
                        .withRestartPolicy(config.restartPolicy)
                        .withImagePullSecrets(
                            listOfNotNull(config.imagePullSecret).map { V1LocalObjectReference().name(it) }
                        )
                       .withServiceAccountName(config.serviceAccountName)
                       .addNewContainer()
                           .withName("${endpoint.configPrefix}-${message.header.traceId}".take(64))
                           .withImage(config.imageName)
                           .withCommand(config.commands)
                           .withArgs(config.args)
                           .withImagePullPolicy(config.imagePullPolicy)
                           .withEnv(
                               (envVars + msgMap).map { V1EnvVarBuilder().withName(it.key).withValue(it.value).build() }
                           )
                           .withVolumeMounts(createVolumeMounts())
                       .endContainer()
                       .withVolumes(createVolumes())
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build()

        api.createNamespacedJob(config.namespace, jobBody, null, null, null, null)
    }

    /**
     * Prepare the environment for the job to create. This environment contains all the variables from the current
     * environment, but if a variable starts with a prefix named like the target [Endpoint], this prefix is removed.
     * This way, variables can be set for specific containers without clashing with other containers.
     */
    private fun createEnvironment(): Map<String, String> {
        val endpointPrefix = "${endpoint.configPrefix.uppercase()}_"
        val endPointVariables = System.getenv().filter { it.key.startsWith(endpointPrefix) }
            .mapKeys { it.key.removePrefix(endpointPrefix) }

        return System.getenv().filterNot { it.key in VARIABLES_NOT_TO_PROPAGATE } + endPointVariables
    }

    /**
     * Return a list with volumes declared in the sender configuration.
     */
    private fun createVolumes(): List<V1Volume> =
        config.secretVolumes.mapIndexed { index, volumeMount ->
            V1Volume()
                .name("$SECRET_VOLUME_PREFIX${index + 1}")
                .secret(V1SecretVolumeSource().secretName(volumeMount.secretName))
        } + config.pvcVolumes.mapIndexed { index, volumeMount ->
            V1Volume()
                .name("$PVC_VOLUME_PREFIX${index + 1}")
                .persistentVolumeClaim(
                    V1PersistentVolumeClaimVolumeSource()
                        .claimName(volumeMount.claimName)
                        .readOnly(volumeMount.readOnly)
                )
        }

    /**
     * Return a list with volume mounts for the volume mount declarations contained in the sender configuration.
     */
    private fun createVolumeMounts(): List<V1VolumeMount> =
        config.secretVolumes.mapIndexed { index, volumeMount ->
            V1VolumeMount()
                .name("$SECRET_VOLUME_PREFIX${index + 1}")
                .mountPath(volumeMount.mountPath)
                .readOnly(true)
        } + config.pvcVolumes.mapIndexed { index, volumeMount ->
            V1VolumeMount()
                .name("$PVC_VOLUME_PREFIX${index + 1}")
                .mountPath(volumeMount.mountPath)
                .readOnly(volumeMount.readOnly)
        }

    /**
     * Create a map with labels to cover the given [traceId]. The [traceId] may be longer than the maximum size
     * allowed for a label value. Therefore, may be split into multiple labels.
     */
    private fun createTraceIdLabels(traceId: String): Map<String, String> =
        traceId.chunked(TRACE_LABEL_LENGTH).withIndex().fold(mapOf()) { map, value ->
            map + ("$TRACE_LABEL_PREFIX${value.index}" to value.value)
        }
}
