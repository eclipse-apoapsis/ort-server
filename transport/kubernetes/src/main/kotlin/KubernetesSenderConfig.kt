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

import com.typesafe.config.Config

import org.ossreviewtoolkit.server.utils.config.getBooleanOrDefault
import org.ossreviewtoolkit.server.utils.config.getIntOrDefault
import org.ossreviewtoolkit.server.utils.config.getStringOrDefault
import org.ossreviewtoolkit.server.utils.config.getStringOrNull

import org.slf4j.LoggerFactory

/**
 * A data class defining a secret to be mounted in a pod. A [KubernetesSenderConfig] contains a list of instances of
 * this class, allowing an arbitrary number of secrets to be mounted into pods.
 */
data class SecretVolumeMount(
    /** The name of the secret to be mounted. */
    val secretName: String,

    /** The path where the secret is to be mounted. */
    val mountPath: String
)

/**
 * A configuration class used by the sender part of the Kubernetes Transport implementation.
 *
 * Note that there is an asymmetry between the configuration requirements of the sender and the receiver part: The
 * sender does the heavy work of creating a Kubernetes job and thus needs to be rather flexible and configurable.
 * The receiver in contrast just receives some parameters from environment variables.
 */
data class KubernetesSenderConfig(
    /** The namespace inside the Kubernetes Cluster. */
    val namespace: String,

    /** The image name for the container that will run in the Pod. */
    val imageName: String,

    /** The policy when pulling images. */
    val imagePullPolicy: String = DEFAULT_IMAGE_PULL_POLICY,

    /** A secret required for pulling the image from the container registry. */
    val imagePullSecret: String? = null,

    /** The restart policy for the job. */
    val restartPolicy: String = DEFAULT_RESTART_POLICY,

    /** The backoff limit when restarting pods. */
    val backoffLimit: Int = DEFAULT_BACKOFF_LIMIT,

    /** The commands to be executed when running the container. */
    val commands: List<String> = emptyList(),

    /** A list with arguments for the container command. */
    val args: List<String> = emptyList(),

    /** A list with [SecretVolumeMount]s to be added to the new pod. */
    val secretVolumes: List<SecretVolumeMount> = emptyList(),

    /** Allows enabling debug logs when interacting with the Kubernetes API. */
    val enableDebugLogging: Boolean = false
) {
    companion object {
        /**
         * The name of this transport implementation, which will be used in the message sender and receiver factories.
         */
        const val TRANSPORT_NAME = "kubernetes"

        /** The name of the configuration property for the Kubernetes namespace. */
        private const val NAMESPACE_PROPERTY = "namespace"

        /** The name of the configuration property for the container image name. */
        private const val IMAGE_NAME_PROPERTY = "imageName"

        /** The name of the configuration property defining the restart policy for jobs. */
        private const val RESTART_POLICY_PROPERTY = "restartPolicy"

        /** The name of the configuration property defining the backoff limit. */
        private const val BACKOFF_LIMIT_PROPERTY = "backoffLimit"

        /** The name of the configuration property defining the image pull policy. */
        private const val IMAGE_PULL_POLICY_PROPERTY = "imagePullPolicy"

        /** The name of the configuration property defining the image pull secret. */
        private const val IMAGE_PULL_SECRET_PROPERTY = "imagePullSecret"

        /** The name of the configuration property for the container commands. */
        private const val COMMANDS_PROPERTY = "commands"

        /** The name of the configuration property for the command arguments. */
        private const val ARGS_PROPERTY = "args"

        /** The name of the configuration property that controls debug logging. */
        private const val ENABLE_DEBUG_LOGGING_PROPERTY = "enableDebugLogging"

        /**
         * The name of the configuration property that defines secrets to be mounted as volumes into the pod to be
         * created. Using this mechanism, external data stored as Kubernetes secrets can be made available to pods.
         * The value of the property consists of a number of mount declarations separated by whitespace. (If a
         * mount declaration contains whitespace itself, it must be surrounded by quotes.) A single mount declaration
         * has the form _secret->path_, where _secret_ is the name of the Kubernetes secret, and _path_ is the path
         * in the filesystem of the pod where the content of the secret is to be mounted. For each mount declaration,
         * the Kubernetes Transport implementation will create one entry under _volumes_ and one entry under
         * _volumeMounts_ in the container specification.
         */
        private const val MOUNT_SECRETS_PROPERTY = "mountSecrets"

        /** The default value for the restart policy property. */
        private const val DEFAULT_RESTART_POLICY = "OnFailure"

        /** Default value for the backoff limit property. */
        private const val DEFAULT_BACKOFF_LIMIT = 2

        /** Default value for the image pull policy property. */
        private const val DEFAULT_IMAGE_PULL_POLICY = "Never"

        private val logger = LoggerFactory.getLogger(KubernetesMessageReceiverFactory::class.java)

        /**
         * A regular expression to split the string with commands. Commands are split at whitespace, except the
         * whitespace is contained in quotes.
         */
        private val splitCommandsRegex = Regex("""\s(?=([^"]*"[^"]*")*[^"]*$)""")

        /** A regular expression to parse a secret volume mount declaration. */
        private val mountDeclarationRegex = Regex("""(\S+)\s*->\s*(.+)""")

        /**
         * Create a [KubernetesSenderConfig] from the provided [config].
         */
        fun createConfig(config: Config) =
            KubernetesSenderConfig(
                namespace = config.getString(NAMESPACE_PROPERTY),
                imageName = config.getString(IMAGE_NAME_PROPERTY),
                imagePullPolicy = config.getStringOrDefault(IMAGE_PULL_POLICY_PROPERTY, DEFAULT_IMAGE_PULL_POLICY),
                imagePullSecret = config.getStringOrNull(IMAGE_PULL_SECRET_PROPERTY),
                restartPolicy = config.getStringOrDefault(RESTART_POLICY_PROPERTY, DEFAULT_RESTART_POLICY),
                backoffLimit = config.getIntOrDefault(BACKOFF_LIMIT_PROPERTY, DEFAULT_BACKOFF_LIMIT),
                commands = config.getStringOrDefault(COMMANDS_PROPERTY, "").splitAtWhitespace(),
                args = config.getStringOrDefault(ARGS_PROPERTY, "").splitAtWhitespace(),
                secretVolumes = config.getStringOrDefault(MOUNT_SECRETS_PROPERTY, "").toVolumeMounts(),
                enableDebugLogging = config.getBooleanOrDefault(ENABLE_DEBUG_LOGGING_PROPERTY, false)
            )

        /**
         * Split this string at whitespace characters unless the whitespace is contained in a part surrounded by
         * quotes.
         */
        private fun String.splitAtWhitespace(): List<String> =
            split(splitCommandsRegex).map { s ->
                if (s.startsWith('"') && s.endsWith('"')) s.substring(1..s.length - 2) else s
            }.filterNot { it.isEmpty() }

        /**
         * Parse this string as a number of [SecretVolumeMount] objects as described at [MOUNT_SECRETS_PROPERTY].
         */
        private fun String.toVolumeMounts(): List<SecretVolumeMount> {
            val (valid, invalid) = splitAtWhitespace().map { it to mountDeclarationRegex.matchEntire(it) }
                .partition { it.second != null }

            if (invalid.isNotEmpty()) {
                val invalidDeclarations = invalid.map { it.first }
                logger.warn(
                    "Found invalid secret volume mount declarations: $invalidDeclarations. These are ignored."
                )
            }

            return valid.mapNotNull { it.second }.map { match ->
                SecretVolumeMount(match.groups[1]?.value!!, match.groups[2]?.value!!)
            }
        }
    }
}
