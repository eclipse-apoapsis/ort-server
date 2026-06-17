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

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.models.V1EnvVar
import io.kubernetes.client.openapi.models.V1EnvVarBuilder
import io.kubernetes.client.openapi.models.V1JobBuilder
import io.kubernetes.client.openapi.models.V1LocalObjectReference
import io.kubernetes.client.openapi.models.V1PodSecurityContextBuilder
import io.kubernetes.client.openapi.models.V1PodSpecFluent
import io.kubernetes.client.openapi.models.V1Volume
import io.kubernetes.client.openapi.models.V1VolumeMount

import java.util.UUID

import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageSender
import org.eclipse.apoapsis.ortserver.transport.RUN_ID_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.TRACE_PROPERTY
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer
import org.eclipse.apoapsis.ortserver.transport.kubernetes.KubernetesSenderConfig.Companion.TRANSPORT_NAME
import org.eclipse.apoapsis.ortserver.transport.selectByPrefix
import org.eclipse.apoapsis.ortserver.utils.config.substituteVariables

/** A prefix for the name of a label storing a part of the trace ID. */
private const val TRACE_LABEL_PREFIX = "trace-id-"

/** The maximum length of the value of a trace label. */
private const val TRACE_LABEL_LENGTH = 60

/** The label to store the current ORT run ID. */
private const val RUN_ID_LABEL = "run-id"

/**
 * The label to store the name of the worker. This is used so that jobs for specific workers can be easily looked
 * up via label selectors.
 */
private const val WORKER_LABEL = "ort-worker"

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

        val variables = message.header.transportProperties.selectByPrefix(TRANSPORT_NAME)
        val env = (createEnvironment() + msgMap).map { V1EnvVarBuilder().withName(it.key).withValue(it.value).build() }
        val labels = config.labels + createTraceIdLabels(traceId) + mapOf(
            RUN_ID_LABEL to message.header.ortRunId.toString(),
            WORKER_LABEL to endpoint.configPrefix
        )
        val (globalMounts, namedMounts) = createVolumeMounts(config)

        val jobBody = V1JobBuilder()
            .withNewMetadata()
                .withName("${endpoint.configPrefix}-${message.header.ortRunId}-$traceId".take(64))
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
                       .addContainers(config, env, variables, globalMounts, namedMounts)
                       .withVolumes(createVolumes(config))
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build()

        api.createNamespacedJob(config.namespace, jobBody).execute()
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
     * Return a list with volumes declared in the given [config].
     */
    private fun createVolumes(config: KubernetesSenderConfig): List<V1Volume> =
        config.volumeMounts.mapIndexed { index, volumeMount ->
            volumeMount.initializeVolume(V1Volume(), index)
        }

    /**
     * Convert the volume mounts declared in the given [config] to [V1VolumeMount] objects. Return a [Pair] with the
     * mounts to be added to all containers and the named volume mounts.
     */
    private fun createVolumeMounts(
        config: KubernetesSenderConfig
    ): Pair<List<V1VolumeMount>, Map<String, V1VolumeMount>> =
        config.volumeMounts.foldIndexed(emptyList<V1VolumeMount>() to emptyMap()) { index, (list, map), volumeMount ->
            val mount = volumeMount.initializeVolumeMount(V1VolumeMount(), index)
                .mountPath(volumeMount.mountPath)
            volumeMount.mountName?.let { name -> list to (map + (name to mount)) } ?: ((list + mount) to map)
        }

    /**
     * Create a map with labels to cover the given [traceId]. The [traceId] may be longer than the maximum size
     * allowed for a label value. Therefore, it may be split into multiple labels.
     */
    private fun createTraceIdLabels(traceId: String): Map<String, String> =
        traceId.chunked(TRACE_LABEL_LENGTH).withIndex().fold(mapOf()) { map, value ->
            map + ("$TRACE_LABEL_PREFIX${value.index}" to value.value)
        }
}

/**
 * Add the containers declared in the given [config] to the pod specification. All containers share the same
 * [environment]. Variable substitution is done based on the provided [variables]. Generate volume mounts based on the
 * given [globalMounts] (which are added to all containers) and [namedMounts] (explicitly referenced by single
 * containers).
 */
private fun <A : V1PodSpecFluent<A>> A.addContainers(
    config: KubernetesSenderConfig,
    environment: List<V1EnvVar>,
    variables: Map<String, String>,
    globalMounts: List<V1VolumeMount>,
    namedMounts: Map<String, V1VolumeMount>
): A =
    (listOf(config.mainContainer) + config.additionalContainers).fold(this) { pod, container ->
        pod.addNewContainer()
            .withName(container.name)
            .withImage(container.imageName.substituteVariables(variables))
            .withImagePullPolicy(container.imagePullPolicy)
            .withCommand(container.commands)
            .withArgs(container.args)
            .withEnv(environment)
            .withResources(container.createResources(variables))
            .withVolumeMounts(globalMounts + container.volumeMounts.mapNotNull { namedMounts[it] })
            .endContainer()
    }
