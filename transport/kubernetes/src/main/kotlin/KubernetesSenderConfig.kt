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

import com.typesafe.config.Config

import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.utils.config.getBooleanOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getIntOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getLongOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull

import org.slf4j.LoggerFactory

/**
 * A configuration class used by the sender part of the Kubernetes Transport implementation.
 *
 * This class contains a larger set of properties that defines the Kubernetes job to be created for the current
 * endpoint. Many aspects of the manifest can be configured, including labels, annotations, retry behavior, volumes,
 * and the containers running in the pod created for the job. Per default, there is only a single container ("main"),
 * whose properties are directly defined in the application configuration. It is, however, possible to declare an
 * arbitrary number of additional containers, which may also be init containers. Their properties are obtained from
 * environment variables.
 *
 * Note that there is an asymmetry between the configuration requirements of the sender and the receiver part: The
 * sender does the heavy work of creating a Kubernetes job and thus needs to be rather flexible and configurable.
 * The receiver in contrast just receives some parameters from environment variables.
 */
data class KubernetesSenderConfig(
    /** The namespace inside the Kubernetes Cluster. */
    val namespace: String,

    /** A secret required for pulling the image from the container registry. */
    val imagePullSecret: String? = null,

    /** The user id to use for running the pod. */
    val userId: Long = DEFAULT_USER_ID,

    /** The restart policy for the job. */
    val restartPolicy: String = DEFAULT_RESTART_POLICY,

    /** The backoff limit when restarting pods. */
    val backoffLimit: Int = DEFAULT_BACKOFF_LIMIT,

    /** The definition of the main container for the job to be created. */
    val mainContainer: Container,

    /** A list storing additional containers that should run in the pod created for the job. */
    val additionalContainers: List<Container> = emptyList(),

    /** A list with volume mount declarations for the pod to be created. */
    val volumeMounts: List<VolumeMount> = emptyList(),

    /**
     * A list of labels to add to the new pod. The labels `ort-worker`, `run-id` and any `trace-id-*` are inserted
     * automatically by the Kubernetes sender and cannot be overridden; matching entries in this map are ignored.
     */
    val labels: Map<String, String> = emptyMap(),

    /**
     * A map with annotations to be added to the new pod. The map defines the keys and values of annotation.
     * Corresponding annotations are added to the _template_ section of newly created jobs, so that they are present
     * in the pods created for these jobs.
     */
    val annotations: Map<String, String> = emptyMap(),

    /** An optional name of a service account to be set for newly created pods. */
    val serviceAccountName: String? = null,

    /** Allows enabling debug logs when interacting with the Kubernetes API. */
    val enableDebugLogging: Boolean = false,

    /** Stores the underlying configuration of the endpoint. */
    private val endpointConfig: Config
) {
    companion object {
        /**
         * The name of this transport implementation, which will be used in the message sender and receiver factories.
         */
        const val TRANSPORT_NAME = "kubernetes"

        /** The name of the configuration property for the Kubernetes namespace. */
        private const val NAMESPACE_PROPERTY = "namespace"

        /**
         * The name of the configuration property for the container image name. The property value is subject to
         * variable substitution.
         */
        private const val IMAGE_NAME_PROPERTY = "imageName"

        /** The name of the configuration property for the user id. */
        private const val USER_ID_PROPERTY = "userId"

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
         * has the form _secret->path|subPath_, where _secret_ is the name of the Kubernetes secret, _path_ is the
         * path in the filesystem of the pod where the content of the secret is to be mounted, and _subPath_ defines
         * a sub path of the secret to be mounted. The _subPath_ component is optional; it defaults to an empty string
         * (corresponding to the volume root). For each mount declaration, the Kubernetes Transport implementation will
         * create one entry under _volumes_ and one entry under _volumeMounts_ in the container specification.
         */
        private const val MOUNT_SECRETS_PROPERTY = "mountSecrets"

        /**
         * The name of the configuration property that defines volume mounts based on persistent volume claims. Via
         * this mechanism, pods can be assigned volumes with shared data. The value of the property consists of a
         * number of mount declarations separated by whitespace. (If a mount declaration contains whitespace itself, it
         * must be surrounded by quotes.) A single mount declaration has the form _pvcName->path,access_, where
         * _pvcName_ is the name of the referenced persistent volume claim, _path_ is the path in the filesystem of
         * the pod where the content of the volume is to be mounted, and _access_ is a flag determining whether the
         * volume is read-only ('R') or writeable ('W').
         */
        private const val MOUNT_PVCS_PROPERTY = "mountPvcs"

        /**
         * The name of the configuration property that defined volume mounts based on empty dirs. This is especially
         * useful to mount writeable directories in environments that enforce read-only root filesystems. The value of
         * the property consists of a number of mount declarations separated by whitespace. If a mount declaration
         * contains whitespace itself, it must be surrounded by quotes. A single mount declaration has the form
         * _name->path_, where _name_ is the name of the empty dir volume and _path_ is the path in the filesystem of
         * the pod where the volume will be mounted.
         */
        private const val MOUNT_EMPTYDIRS_PROPERTY = "mountEmptyDirs"

        /**
         * The name of the configuration that defines the names of volume mounts to be added to the main container. The
         * value is a comma-separated list of mount names. (Note that all volume mounts without a name are added
         * automatically.)
         */
        private const val VOLUME_MOUNT_NAMES_PROPERTY = "volumeMounts"

        /**
         * The name of the configuration property defining the labels to add to new pods. The value of this property is
         * interpreted as a comma-separated list of labels, ignoring whitespace around the labels. Each label must have
         * the format _key=value_. The labels `ort-worker`, `run-id` and any `trace-id-*` are inserted automatically by
         * the Kubernetes sender and cannot be overridden; matching entries in this list are ignored.
         */
        private const val LABELS_PROPERTY = "labels"

        /**
         * The name of the configuration property that allows defining annotations based on environment variables.
         * If available, the value of this property is interpreted as a comma-separated list of environment variable
         * names. Each variable is looked up in the current environment. It must have the form _key=value_. The
         * Kubernetes sender then creates an annotation with the given key and value for the template of the new job.
         */
        private const val ANNOTATIONS_VARIABLES_PROPERTY = "annotationVariables"

        /** The name of the configuration property defining the name of the service account for pods. */
        private const val SERVICE_ACCOUNT_PROPERTY = "serviceAccount"

        /**
         * The name of the configuration property defining the limit for the CPU resource. The property value is
         * subject to variable substitution.
         */
        private const val CPU_LIMIT_PROPERTY = "cpuLimit"

        /**
         * The name of the configuration property defining the limit for the memory resource. The property value is
         * subject to variable substitution.
         */
        private const val MEMORY_LIMIT_PROPERTY = "memoryLimit"

        /**
         * The name of the configuration property defining the request for the CPU resource. The property value is
         * subject to variable substitution.
         */
        private const val CPU_REQUEST_PROPERTY = "cpuRequest"

        /**
         * The name of the configuration property defining the request for the memory resource. The property value is
         * subject to variable substitution.
         */
        private const val MEMORY_REQUEST_PROPERTY = "memoryRequest"

        /**
         * The name of the configuration property that allows the definition of additional containers. The property
         * value is expected to be a comma-separated list of container names. The properties of these containers (such
         * as image name, commands, args, etc.) are then obtained from environment variables following a certain naming
         * convention. The variables must start with the container name, followed by an underscore, followed by the
         * property name in upper snake case, for instance, `CONTROLLER_IMAGE_NAME` or `CONTROLLER_CPU_REQUEST` for a
         * container named _CONTROLLER_. The additional containers inherit a number of properties from the main
         * container unless they are overridden by the corresponding variables. There are some exceptions like
         * resources and volume mounts which need to be explicitly defined for each container.
         */
        private const val ADDITIONAL_CONTAINERS_PROPERTY = "additionalContainers"

        /** The default value for the user id. */
        private const val DEFAULT_USER_ID = 1000L

        /** The default value for the restart policy property. */
        private const val DEFAULT_RESTART_POLICY = "OnFailure"

        /** Default value for the backoff limit property. */
        private const val DEFAULT_BACKOFF_LIMIT = 2

        /** Default value for the image pull policy property. */
        private const val DEFAULT_IMAGE_PULL_POLICY = "Never"

        /** The separator used for annotation variables to extract the key and the value. */
        private const val KEY_VALUE_SEPARATOR = '='

        /** The separator character used in string lists. */
        private const val LIST_SEPARATOR = ','

        private val logger = LoggerFactory.getLogger(KubernetesSenderConfig::class.java)

        /**
         * A regular expression to split the string with commands. Commands are split at whitespace, except the
         * whitespace is contained in quotes.
         */
        private val splitCommandsRegex = Regex("""\s(?=([^"]*"[^"]*")*[^"]*$)""")

        /** A regular expression to split comma-separated lists, like the list of labels. */
        private val splitCommaListRegex = splitRegex(LIST_SEPARATOR)

        /** A regular expression to split the list with environment variables defining annotations. */
        private val splitAnnotationVariablesRegex = splitRegex(LIST_SEPARATOR)

        /** A regular expression to split key value pairs. */
        private val splitKeyValueRegex = splitRegex(KEY_VALUE_SEPARATOR)

        /**
         * Create a [KubernetesSenderConfig] for the given [endpoint] from the provided [config].
         */
        fun createConfig(config: Config, endpoint: Endpoint<*>): KubernetesSenderConfig {
            val mainContainer = createMainContainer(config, endpoint)
            val additionalContainerNames = config.getStringOrNull(ADDITIONAL_CONTAINERS_PROPERTY)
                .splitAt(splitCommaListRegex)

            return KubernetesSenderConfig(
                namespace = config.getString(NAMESPACE_PROPERTY),
                imagePullSecret = config.getStringOrNull(IMAGE_PULL_SECRET_PROPERTY),
                userId = config.getLongOrDefault(USER_ID_PROPERTY, DEFAULT_USER_ID),
                restartPolicy = config.getStringOrDefault(RESTART_POLICY_PROPERTY, DEFAULT_RESTART_POLICY),
                backoffLimit = config.getIntOrDefault(BACKOFF_LIMIT_PROPERTY, DEFAULT_BACKOFF_LIMIT),
                volumeMounts = config.parseSecretVolumeMounts() + config.parsePvcVolumeMounts() +
                        config.parseEmptyDirVolumeMounts(),
                labels = createLabels(config.getStringOrDefault(LABELS_PROPERTY, "")),
                annotations = createAnnotations(config.getStringOrDefault(ANNOTATIONS_VARIABLES_PROPERTY, "")),
                serviceAccountName = config.getStringOrNull(SERVICE_ACCOUNT_PROPERTY),
                enableDebugLogging = config.getBooleanOrDefault(ENABLE_DEBUG_LOGGING_PROPERTY, false),
                mainContainer = mainContainer,
                additionalContainers = additionalContainerNames.map { createAdditionalContainer(it, mainContainer) },
                endpointConfig = config
            )
        }

        /**
         * Create a [Container] object representing the main container of the pod for the given [endpoint] from the
         * given [config].
         */
        private fun createMainContainer(config: Config, endpoint: Endpoint<*>): Container =
            Container(
                name = "${endpoint.configPrefix}-main",
                imageName = config.getString(IMAGE_NAME_PROPERTY),
                imagePullPolicy = config.getStringOrDefault(IMAGE_PULL_POLICY_PROPERTY, DEFAULT_IMAGE_PULL_POLICY),
                commands = config.getStringOrDefault(COMMANDS_PROPERTY, "").splitAtWhitespace(),
                args = config.getStringOrDefault(ARGS_PROPERTY, "").splitAtWhitespace(),
                cpuLimit = config.getStringOrNull(CPU_LIMIT_PROPERTY),
                cpuRequest = config.getStringOrNull(CPU_REQUEST_PROPERTY),
                memoryLimit = config.getStringOrNull(MEMORY_LIMIT_PROPERTY),
                memoryRequest = config.getStringOrNull(MEMORY_REQUEST_PROPERTY),
                volumeMounts = config.getStringOrNull(VOLUME_MOUNT_NAMES_PROPERTY).splitAt(splitCommaListRegex)
            )

        /**
         * Create a [Container] object for the additional container with the given [name]. Look for environment
         * variables starting with a prefix derived from the container name. User properties from the given
         * [mainContainer] as default values.
         */
        private fun createAdditionalContainer(name: String, mainContainer: Container): Container {
            val prefix = name.uppercase() + "_"

            return Container(
                name = "${name.lowercase()}-container",
                imageName = System.getenv("${prefix}IMAGE_NAME") ?: mainContainer.imageName,
                imagePullPolicy = System.getenv("${prefix}IMAGE_PULL_POLICY") ?: mainContainer.imagePullPolicy,
                commands = System.getenv("${prefix}COMMANDS")?.splitAtWhitespace() ?: mainContainer.commands,
                args = System.getenv("${prefix}ARGS")?.splitAtWhitespace() ?: mainContainer.args,
                cpuLimit = System.getenv("${prefix}CPU_LIMIT"),
                cpuRequest = System.getenv("${prefix}CPU_REQUEST"),
                memoryLimit = System.getenv("${prefix}MEMORY_LIMIT"),
                memoryRequest = System.getenv("${prefix}MEMORY_REQUEST"),
                volumeMounts = System.getenv("${prefix}VOLUME_MOUNTS").splitAt(splitCommaListRegex),
                isInitContainer = System.getenv("${prefix}INIT_CONTAINER")?.toBoolean() ?: false
            )
        }

        /**
         * Return a [Regex] that can be used to split a string at the given [separator] and that handles whitespace
         * around the separator correctly.
         */
        private fun splitRegex(separator: Char): Regex = Regex("""\s*$separator\s*""")

        /**
         * Split this string at whitespace characters unless the whitespace is contained in a part surrounded by
         * quotes.
         */
        private fun String.splitAtWhitespace(): List<String> =
            split(splitCommandsRegex).map { s ->
                if (s.startsWith('"') && s.endsWith('"')) s.substring(1..s.length - 2) else s
            }.filterNot { it.isEmpty() }

        /**
         * Split a nullable string using the given [regex] and return a list with the non-empty parts.
         */
        private fun String?.splitAt(regex: Regex): List<String> =
            this?.split(regex).orEmpty().filterNot { it.isEmpty() }

        /**
         * Parse the given [property] of this [Config] as a number of volume mount objects making use of the provided
         * [parse] function.
         */
        private fun Config.toVolumeMounts(property: String, parse: (String) -> VolumeMount?): List<VolumeMount> {
            val declarations = getStringOrDefault(property, "")
            return declarations.splitAtWhitespace().mapNotNull(parse)
        }

        /**
         * Parse the secret volume mount declarations from this [Config].
         */
        private fun Config.parseSecretVolumeMounts(): List<VolumeMount> =
            toVolumeMounts(MOUNT_SECRETS_PROPERTY, ::parseSecretVolumeMount)

        /**
         * Parse the PVC-based volume mount declarations from this [Config].
         */
        private fun Config.parsePvcVolumeMounts(): List<VolumeMount> =
            toVolumeMounts(MOUNT_PVCS_PROPERTY, ::parsePvcVolumeMount)

        /**
         * Parse the empty volume mount declarations from this [Config].
         */
        private fun Config.parseEmptyDirVolumeMounts(): List<VolumeMount> =
            toVolumeMounts(MOUNT_EMPTYDIRS_PROPERTY, ::parseEmptyVolumeMount)

        /**
         * Create the map with labels based on the given [labels]. Extract the labels from the comma-delimited string,
         * ignoring whitespace around the key value pairs. Each label must have the format _key=value_. Ignore invalid
         * labels, but log a warning for them. Also ignore labels with reserved keys, but log a warning for them as
         * well.
         */
        private fun createLabels(labels: String): Map<String, String> {
            if (labels.isEmpty()) return emptyMap()

            val reservedLabels = listOf("ort-worker", "run-id")

            return labels.split(splitCommaListRegex).mapNotNull { label ->
                val keyValue = label.split(splitKeyValueRegex, limit = 2)
                if (keyValue.size != 2 || keyValue[0].isEmpty() || keyValue[1].isEmpty()) {
                    logger.warn("Ignore invalid label declaration: '$label'. Labels must have the format 'key=value'.")
                    return@mapNotNull null
                }

                if (keyValue[0] in reservedLabels || keyValue[0].startsWith("trace-id-")) {
                    logger.warn(
                        "Ignore label with reserved key '${keyValue[0]}'. The keys 'ort-worker', 'run-id' and " +
                                "'trace-id-*' are reserved and cannot be used in custom labels."
                    )
                    return@mapNotNull null
                }

                keyValue[0] to keyValue[1]
            }.toMap()
        }

        /**
         * Create the map with annotations based on the given string with [variableNames]. Extract the names from the
         * comma-delimited string. Look up the values of the referenced variables and extract the keys and values of
         * annotations from them. Ignore non-existing or invalid variables, but log them.
         */
        private fun createAnnotations(variableNames: String): Map<String, String> {
            if (variableNames.isEmpty()) return emptyMap()

            val (annotations, invalid) = variableNames.split(splitAnnotationVariablesRegex)
                .map { variable -> variable to (System.getenv(variable).orEmpty()) }
                .partition { KEY_VALUE_SEPARATOR in it.second }

            if (invalid.isNotEmpty()) {
                val invalidNames = invalid.map { it.first }
                logger.warn("Found invalid variables for annotations: $invalidNames")
                logger.warn(
                    "Make sure that these variables are defined and have the format 'key${KEY_VALUE_SEPARATOR}value'."
                )
            }

            return annotations.associate {
                val keyValue = it.second.split(splitKeyValueRegex, limit = 2)
                keyValue[0] to keyValue[1]
            }
        }
    }
}
