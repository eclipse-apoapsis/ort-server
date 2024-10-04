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

import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.models.V1EnvVarBuilder
import io.kubernetes.client.openapi.models.V1JobBuilder
import io.kubernetes.client.openapi.models.V1LocalObjectReference
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource
import io.kubernetes.client.openapi.models.V1PodSecurityContextBuilder
import io.kubernetes.client.openapi.models.V1ResourceRequirements
import io.kubernetes.client.openapi.models.V1SecretVolumeSource
import io.kubernetes.client.openapi.models.V1Volume
import io.kubernetes.client.openapi.models.V1VolumeMount

import java.util.UUID

import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageSender
import org.eclipse.apoapsis.ortserver.transport.RUN_ID_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.TRACE_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer

/** A prefix for the name of a label storing a part of the trace ID. */
private const val TRACE_LABEL_PREFIX = "trace-id-"

/** The maximum length of the value of a trace label. */
private const val TRACE_LABEL_LENGTH = 60

/** The label to store the current ORT run ID. */
private const val RUN_ID_LABEL = "run-id"

/**
 * The label to store the name of the worker. This is used, so that jobs for specific workers can be easily looked
 * up via label selectors.
 */
private const val WORKER_LABEL = "ort-worker"

/** A prefix for generating names for secret volumes. */
private const val SECRET_VOLUME_PREFIX = "secret-volume-"

/** A prefix for generating names for PVC volumes. */
private const val PVC_VOLUME_PREFIX = "pvc-volume-"

/** The name of the CPU resource. */
private const val CPU_RESOURCE = "cpu"

/** The name of the memory resource. */
private const val MEMORY_RESOURCE = "memory"

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
        val traceId = validTraceId(message)
        val msgMap = mapOf(
            TRACE_PROPERTY to traceId,
            RUN_ID_PROPERTY to message.header.ortRunId.toString(),
            "payload" to serializer.toJson(message.payload)
        )

        val msgConfig = config.forMessage(message)
        val envVars = createEnvironment()
        val labels = createTraceIdLabels(traceId) + mapOf(
            RUN_ID_LABEL to message.header.ortRunId.toString(),
            WORKER_LABEL to endpoint.configPrefix
        )

        val jobBody = V1JobBuilder()
            .withNewMetadata()
                .withName("${endpoint.configPrefix}-$traceId".take(64))
                .withLabels<String, String>(labels)
            .endMetadata()
            .withNewSpec()
                .withBackoffLimit(msgConfig.backoffLimit)
                .withNewTemplate()
                    .withNewMetadata()
                        .withAnnotations<String, String>(msgConfig.annotations)
                        .withLabels<String, String>(labels)
                    .endMetadata()
                    .withNewSpec()
                        .withSecurityContext(V1PodSecurityContextBuilder().withRunAsUser(msgConfig.userId).build())
                        .withRestartPolicy(msgConfig.restartPolicy)
                        .withImagePullSecrets(
                            listOfNotNull(msgConfig.imagePullSecret).map { V1LocalObjectReference().name(it) }
                        )
                       .withServiceAccountName(msgConfig.serviceAccountName)
                       .addNewContainer()
                           .withName("${endpoint.configPrefix}-$traceId".take(64))
                           .withImage(msgConfig.imageName)
                           .withCommand(msgConfig.commands)
                           .withArgs(msgConfig.args)
                           .withImagePullPolicy(msgConfig.imagePullPolicy)
                           .withEnv(
                               (envVars + msgMap).map { V1EnvVarBuilder().withName(it.key).withValue(it.value).build() }
                           )
                           .withVolumeMounts(createVolumeMounts(msgConfig))
                           .withResources(createResources(msgConfig))
                       .endContainer()
                       .withVolumes(createVolumes(msgConfig))
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build()

        runCatching {
            api.createNamespacedJob(msgConfig.namespace, jobBody, null, null, null, null)
        }.onFailure { e ->
            println("ApiException: ${e.message} | ${e.cause?.message}")
            if (e is ApiException) {
                println("ApiException: ${e.code} | ${e.responseBody}")
            }
        }
    }

    /**
     * Generate a valid trace ID from the given [message]. Check the ID contained in the message. If it is empty, a
     * unique ID is generated. This is needed because the job name is derived from the trace ID, and Kubernetes
     * as some restrictions for such names.
     */
    private fun validTraceId(message: Message<T>): String =
        (message.header.traceId.takeUnless { it.isBlank() } ?: UUID.randomUUID()).toString()

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
     * Return a list with volumes declared in the given [msgConfig].
     */
    private fun createVolumes(msgConfig: KubernetesSenderConfig): List<V1Volume> =
        msgConfig.secretVolumes.mapIndexed { index, volumeMount ->
            V1Volume()
                .name("$SECRET_VOLUME_PREFIX${index + 1}")
                .secret(V1SecretVolumeSource().secretName(volumeMount.secretName))
        } + msgConfig.pvcVolumes.mapIndexed { index, volumeMount ->
            V1Volume()
                .name("$PVC_VOLUME_PREFIX${index + 1}")
                .persistentVolumeClaim(
                    V1PersistentVolumeClaimVolumeSource()
                        .claimName(volumeMount.claimName)
                        .readOnly(volumeMount.readOnly)
                )
        }

    /**
     * Return a list with volume mounts for the volume mount declarations contained in the given [msgConfig].
     */
    private fun createVolumeMounts(msgConfig: KubernetesSenderConfig): List<V1VolumeMount> =
        msgConfig.secretVolumes.mapIndexed { index, volumeMount ->
            V1VolumeMount()
                .name("$SECRET_VOLUME_PREFIX${index + 1}")
                .mountPath(volumeMount.mountPath)
                .subPath(volumeMount.subPath)
                .readOnly(true)
        } + msgConfig.pvcVolumes.mapIndexed { index, volumeMount ->
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

    /**
     * Create a [V1ResourceRequirements] object based on the given [msgConfig]. If no resource requirements are
     * specified, return *null*.
     */
    private fun createResources(msgConfig: KubernetesSenderConfig): V1ResourceRequirements? {
        val requirements = V1ResourceRequirements()

        msgConfig.cpuLimit?.let { requirements.putLimitsItem(CPU_RESOURCE, Quantity(it)) }
        msgConfig.memoryLimit?.let { requirements.putLimitsItem(MEMORY_RESOURCE, Quantity(it)) }
        msgConfig.cpuRequest?.let { requirements.putRequestsItem(CPU_RESOURCE, Quantity(it)) }
        msgConfig.memoryRequest?.let { requirements.putRequestsItem(MEMORY_RESOURCE, Quantity(it)) }

        return requirements.takeUnless { it.limits.isNullOrEmpty() && it.requests.isNullOrEmpty() }
    }
}
