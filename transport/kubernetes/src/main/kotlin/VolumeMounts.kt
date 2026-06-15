/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource
import io.kubernetes.client.openapi.models.V1SecretVolumeSource
import io.kubernetes.client.openapi.models.V1Volume
import io.kubernetes.client.openapi.models.V1VolumeMount

import org.slf4j.LoggerFactory

/** A prefix for generating names for secret volumes. */
private const val SECRET_VOLUME_PREFIX = "secret-volume-"

/** A prefix for generating names for PVC volumes. */
private const val PVC_VOLUME_PREFIX = "pvc-volume-"

/** A regular expression to parse a secret volume mount declaration. */
private val mountSecretDeclarationRegex = Regex("""(\S+)\s*->\s*([^|]+)(?:(?:\s*)\|\s*(.+))?""")

/** A regular expression to parse a PVC-based volume mount declaration. */
private val mountPvcDeclarationRegex = Regex("""(\S+)\s*->\s*([^,]+),([RrWw])""")

private val mountEmptyDirDeclarationRegex = Regex("""(\S+)\s*->\s*([^,]+)""")

private val logger = LoggerFactory.getLogger("VolumeMounts")

/**
 * An interface describing a volume mount for a Kubernetes container.
 *
 * The Kubernetes transport implementation supports different types of volumes that can be mounted into the containers
 * created by the sender. This interface allows treating the different volume types in a uniform way. Based on the
 * information stored in concrete instances, the sender can generate both `volumes` and `volumeMounts` sections in the
 * generated manifests.
 */
sealed interface VolumeMount {
    /** The path where to mount the volume in the container. */
    val mountPath: String

    /**
     * Populate the properties of the passed in [mount] according to the data stored in this object. Use the given
     * [index] to generate properties that need to be unique, such as a volume name. The sender implementation calls
     * this function when it constructs the Kubernetes manifest for the pod to create.
     */
    fun initializeVolumeMount(mount: V1VolumeMount, index: Int): V1VolumeMount

    /**
     * Populate the properties of the passed in [volume] according to the data stored in this object. Use the given
     * [index] to generate properties that need to be unique, such as a volume name. The sender implementation calls
     * this function when it constructs the Kubernetes manifest for the pod to create.
     */
    fun initializeVolume(volume: V1Volume, index: Int): V1Volume
}

/**
 * A data class defining a volume for a secret to be mounted in a container.
 */
internal data class SecretVolumeMount(
    /** The name of the secret to be mounted. */
    val secretName: String,

    /** The path where the secret is to be mounted. */
    override val mountPath: String,

    /** The optional sub path to mount from the volume. */
    val subPath: String? = null
) : VolumeMount {
    override fun initializeVolumeMount(mount: V1VolumeMount, index: Int): V1VolumeMount =
        mount.name("$SECRET_VOLUME_PREFIX${index + 1}")
            .subPath(subPath)
            .readOnly(true)

    override fun initializeVolume(
        volume: V1Volume,
        index: Int
    ): V1Volume =
        volume.name("$SECRET_VOLUME_PREFIX${index + 1}")
            .secret(V1SecretVolumeSource().secretName(secretName))
}

/**
 * A data class defining a volume mount for a pod based on a persistent volume claim.
 */
internal data class PvcVolumeMount(
    /** The name of the referenced persistent volume claim. */
    val claimName: String,

    /** The path where the volume is mounted into the pod. */
    override val mountPath: String,

    /** A flag whether this is a read-only volume. */
    val readOnly: Boolean
) : VolumeMount {
    override fun initializeVolumeMount(mount: V1VolumeMount, index: Int): V1VolumeMount =
        mount.name("$PVC_VOLUME_PREFIX${index + 1}")
            .readOnly(readOnly)

    override fun initializeVolume(volume: V1Volume, index: Int): V1Volume =
        volume.name("$PVC_VOLUME_PREFIX${index + 1}")
            .persistentVolumeClaim(
                V1PersistentVolumeClaimVolumeSource()
                    .claimName(claimName)
                    .readOnly(readOnly)
            )
}

/** A data class defining a volume mount for an empty dir. */
data class EmptyDirVolumeMount(
    /** The name of the empty dir volume. */
    val name: String,

    /** The path where the volume is mounted into the pod. */
    override val mountPath: String
) : VolumeMount {
    override fun initializeVolumeMount(mount: V1VolumeMount, index: Int): V1VolumeMount =
        mount.name(name)

    override fun initializeVolume(
        volume: V1Volume,
        index: Int
    ): V1Volume =
        volume.name(name)
            .emptyDir(V1EmptyDirVolumeSource())
}

/**
 * Parse the given [mountDeclaration] for a secret volume and return the corresponding [VolumeMount] or *null* if the
 * declaration is invalid.
 */
internal fun parseSecretVolumeMount(mountDeclaration: String): VolumeMount? =
    parseVolumeMount(mountDeclaration, mountSecretDeclarationRegex) { match ->
        val (secretName, mountPath, subPath) = match.destructured
        SecretVolumeMount(secretName, mountPath.trim(), subPath.takeUnless { it.isEmpty() })
    }

/**
 * Parse the given [mountDeclaration] for a persistent volume claim and return the corresponding [VolumeMount] or
 * *null* if the declaration is invalid.
 */
internal fun parsePvcVolumeMount(mountDeclaration: String): VolumeMount? =
    parseVolumeMount(mountDeclaration, mountPvcDeclarationRegex) { match ->
        val (claimName, mountPath, readOnly) = match.destructured
        PvcVolumeMount(claimName, mountPath, readOnly.lowercase() == "r")
    }

/**
 *  * Parse the given [mountDeclaration] for an empty volume and return the corresponding [VolumeMount] or *null* if
 *  the declaration is invalid.
 */
internal fun parseEmptyVolumeMount(mountDeclaration: String): VolumeMount? =
    parseVolumeMount(mountDeclaration, mountEmptyDirDeclarationRegex) { match ->
        val (name, mountPath) = match.destructured
        EmptyDirVolumeMount(name, mountPath)
    }

/**
 * Parse the given [mountDeclaration] string using a given [regex] and convert it to a [VolumeMount] using a provided
 * [parse] function. Return *null* if the declaration is invalid.
 */
private fun parseVolumeMount(
    mountDeclaration: String,
    regex: Regex,
    parse: (MatchResult) -> VolumeMount
): VolumeMount? {
    val matchResult = regex.matchEntire(mountDeclaration)

    return if (matchResult == null) {
        logger.warn("Found invalid volume mount declaration: $mountDeclaration. This will be ignored.")
        null
    } else {
        parse(matchResult)
    }
}
