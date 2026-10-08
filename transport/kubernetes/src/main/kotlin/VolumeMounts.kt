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
private val mountPvcDeclarationRegex = Regex("""(\S+)\s*->\s*([^|,]+)(?:\|\s*([^,]+))?,([RrWw])""")

/** A regular expression to parse an empty volume mount declaration. */
private val mountEmptyDirDeclarationRegex = Regex("""(\S+)\s*->\s*([^|]+)(?:\s*\|\s*(.+))?""")

/** A regular expression to parse an expression with an optional preceding name assignment. */
private val namedDeclarationRegex = Regex("""((\S+)\s*=\s*)?(.+)""")

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
     * An optional name assigned to this volume mount declaration. This is used to reference mount declarations by
     * name, for instance, to specify which mount should be added to a specific container. Mount declarations without
     * a name are added to all containers.
     */
    val mountName: String?

    /**
     * An optional sub path to mount from the volume. This is used to mount a specific file or directory from the volume
     * instead of the whole volume.
     */
    val subPath: String?

    /**
     * The typed identity of the underlying Kubernetes resource (e.g. a secret or a persistent volume claim) backing
     * this mount. Multiple mount declarations referencing the same resource of the same type have an equal
     * [VolumeIdentity]. This is used to deduplicate the `volumes` generated for a pod and to link a `volumeMount` to
     * its corresponding `volume`. Using a typed identity - rather than, for instance, the generated Kubernetes volume
     * name - prevents mounts that reference different kinds of resources from being incorrectly merged just because
     * they happen to produce the same name.
     */
    val volumeIdentity: VolumeIdentity

    /**
     * Populate the properties of the passed in [mount] according to the data stored in this object, using the given
     * [volumeName] as the name of the referenced Kubernetes volume. The sender implementation calls this function
     * when it constructs the Kubernetes manifest for the pod to create.
     */
    fun initializeVolumeMount(mount: V1VolumeMount, volumeName: String): V1VolumeMount

    /**
     * Populate the properties of the passed in [volume] according to the data stored in this object, using the
     * given [volumeName] as the name of the Kubernetes volume. The sender implementation calls this function when it
     * constructs the Kubernetes manifest for the pod to create.
     */
    fun initializeVolume(volume: V1Volume, volumeName: String): V1Volume
}

/**
 * A sealed interface describing the identity of the concrete Kubernetes resource backing a [VolumeMount]. Instances
 * are used as keys to deduplicate volume mounts that reference the same resource, independent of the (generated)
 * name of the resulting Kubernetes volume.
 */
sealed interface VolumeIdentity {
    /** The identity of a volume based on a Kubernetes secret with the given [secretName]. */
    data class Secret(val secretName: String) : VolumeIdentity

    /** The identity of a volume based on a persistent volume claim with the given [claimName]. */
    data class Pvc(val claimName: String) : VolumeIdentity

    /** The identity of an empty dir volume with the given [name]. */
    data class EmptyDir(val name: String) : VolumeIdentity
}

/**
 * Generate a Kubernetes-compliant name for the volume identified by [identity]. The generated name must be a valid
 * DNS label (at most 63 characters, consisting only of lowercase alphanumeric characters or '-'). Since the name of
 * the backing resource (e.g. a secret name, which is a DNS subdomain that may contain dots and be much longer than
 * 63 characters) is not guaranteed to fulfill these constraints, the name is not derived from it directly. Instead,
 * a stable [index] - the position of this identity within the deduplicated list of volume mounts of a pod - is used
 * to generate a short, deterministic, and always valid name.
 */
internal fun generateVolumeName(identity: VolumeIdentity, index: Int): String =
    when (identity) {
        is VolumeIdentity.Secret -> "$SECRET_VOLUME_PREFIX${index + 1}"
        is VolumeIdentity.Pvc -> "$PVC_VOLUME_PREFIX${index + 1}"
        is VolumeIdentity.EmptyDir -> identity.name
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
    override val subPath: String? = null,

    override val mountName: String? = null
) : VolumeMount {
    override val volumeIdentity: VolumeIdentity = VolumeIdentity.Secret(secretName)

    override fun initializeVolumeMount(mount: V1VolumeMount, volumeName: String): V1VolumeMount =
        mount.name(volumeName)
            .subPath(subPath)
            .readOnly(true)

    override fun initializeVolume(volume: V1Volume, volumeName: String): V1Volume =
        volume.name(volumeName)
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
    val readOnly: Boolean,

    override val mountName: String? = null,

    override val subPath: String? = null
) : VolumeMount {
    override val volumeIdentity: VolumeIdentity = VolumeIdentity.Pvc(claimName)

    override fun initializeVolumeMount(mount: V1VolumeMount, volumeName: String): V1VolumeMount =
        mount.name(volumeName)
            .subPath(subPath)
            .readOnly(readOnly)

    override fun initializeVolume(volume: V1Volume, volumeName: String): V1Volume =
        volume.name(volumeName)
            .persistentVolumeClaim(V1PersistentVolumeClaimVolumeSource().claimName(claimName))
}

/** A data class defining a volume mount for an empty dir. */
data class EmptyDirVolumeMount(
    /** The name of the empty dir volume. */
    val name: String,

    /** The path where the volume is mounted into the pod. */
    override val mountPath: String,

    override val mountName: String? = null,

    override val subPath: String? = null
) : VolumeMount {
    override val volumeIdentity: VolumeIdentity = VolumeIdentity.EmptyDir(name)

    override fun initializeVolumeMount(mount: V1VolumeMount, volumeName: String): V1VolumeMount =
        mount.name(volumeName)
            .subPath(subPath)

    override fun initializeVolume(volume: V1Volume, volumeName: String): V1Volume =
        volume.name(volumeName)
            .emptyDir(V1EmptyDirVolumeSource())
}

/**
 * Parse the given [mountDeclaration] for a secret volume and return the corresponding [VolumeMount] or *null* if the
 * declaration is invalid.
 */
internal fun parseSecretVolumeMount(mountDeclaration: String): VolumeMount? =
    parseVolumeMount(mountDeclaration, mountSecretDeclarationRegex) { match, name ->
        val (secretName, mountPath, subPath) = match.destructured
        SecretVolumeMount(secretName, mountPath.trim(), subPath.trim().takeUnless { it.isEmpty() }, name)
    }

/**
 * Parse the given [mountDeclaration] for a persistent volume claim and return the corresponding [VolumeMount] or
 * *null* if the declaration is invalid.
 */
@Suppress("DestructuringDeclarationWithTooManyEntries")
internal fun parsePvcVolumeMount(mountDeclaration: String): VolumeMount? =
    parseVolumeMount(mountDeclaration, mountPvcDeclarationRegex) { match, name ->
        val (claimName, mountPath, subPath, readOnly) = match.destructured
        PvcVolumeMount(
            claimName,
            mountPath.trim(),
            readOnly.lowercase() == "r",
            name,
            subPath.trim().takeUnless { it.isEmpty() }
        )
    }

/**
 *  * Parse the given [mountDeclaration] for an empty volume and return the corresponding [VolumeMount] or *null* if
 *  the declaration is invalid.
 */
internal fun parseEmptyVolumeMount(mountDeclaration: String): VolumeMount? =
    parseVolumeMount(mountDeclaration, mountEmptyDirDeclarationRegex) { match, mountName ->
        val (name, mountPath, subPath) = match.destructured
        EmptyDirVolumeMount(name, mountPath.trim(), mountName, subPath.trim().takeUnless { it.isEmpty() })
    }

/**
 * Parse the given [mountDeclaration] string using a given [regex] and convert it to a [VolumeMount] using a provided
 * [parse] function. Return *null* if the declaration is invalid.
 */
private fun parseVolumeMount(
    mountDeclaration: String,
    regex: Regex,
    parse: (MatchResult, String?) -> VolumeMount
): VolumeMount? =
    namedDeclarationRegex.matchEntire(mountDeclaration)?.let { match ->
        val name = match.groupValues[2].takeIf { it.isNotEmpty() }
        val declaration = match.groupValues[3]

        val matchResult = regex.matchEntire(declaration)

        if (matchResult == null) {
            logger.warn("Found invalid volume mount declaration: $mountDeclaration. This will be ignored.")
            null
        } else {
            parse(matchResult, name)
        }
    }
