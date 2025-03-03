/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.secrets.scaleway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

internal val json = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
}

/**
 * The response returned from https://www.scaleway.com/en/developers/api/secret-manager/#path-secrets-list-secrets.
 */
@Serializable
internal data class SecretsListResponse(
    /** Single page of secrets matching the requested criteria. */
    val secrets: List<ScalewaySecret>,

    /** Count of all secrets matching the requested criteria. */
    val totalCount: Int
)

/**
 * The request to use with https://www.scaleway.com/en/developers/api/secret-manager/#path-secrets-create-a-secret.
 */
@Serializable
internal data class SecretCreateRequest(
    /** ID of the project containing the secret. (UUID format) */
    val projectId: String,

    /** Name of the secret. */
    val name: String,

    /** List of the secret's tags. */
    val tags: List<String> = emptyList(),

    /** Description of the secret. */
    val description: String? = null,

    /** Type of the secret. */
    val type: SecretType = SecretType.OPAQUE,

    /** Location of the secret in the directory structure. If not specified, the path is "/". */
    val path: String? = null,

    /** Policy that defines whether / when a secret's version expires. Applies to all the versions by default. */
    val ephemeralPolicy: EphemeralPolicy? = null,

    /** Protect a secret from deletion. */
    val protected: Boolean = false
)

/**
 * See https://www.scaleway.com/en/docs/secret-manager/concepts/#secret-types.
 */
@Serializable
internal enum class SecretType {
    /** Unknown type. */
    @SerialName("unknown_type") UNKNOWN,

    /** Default type. */
    @SerialName("opaque") OPAQUE,

    /** List of concatenated PEM blocks. They can contain certificates, private keys or any other PEM block types. */
    @SerialName("certificate") CERTIFICATE,

    /** Flat JSON structure that allows to set as many first level keys and scalar types as values as needed. */
    @SerialName("key_value") KEY_VALUE,

    /** Flat JSON structure that allows to set a username and a password. */
    @SerialName("basic_credentials") BASIC_CREDENTIALS,

    /** Flat JSON structure that allows to set an engine, username, password, host, database name, and port. */
    @SerialName("database_credentials") DATABASE_CREDENTIALS,

    /** Flat JSON structure that allows to set an SSH key. */
    @SerialName("ssh_key") SSH_KEY
}

@Serializable
internal data class EphemeralPolicy(
    /** Time frame (in seconds), from one second and up to one year, during which the secret's versions are valid. */
    val timeToLive: String? = null,

    /** Set to true if the secret expires after a single user access. */
    val expiresOnceAccessed: Boolean? = null,

    /** The action to perform when a version of the secret expires. */
    val action: ExpirationAction
)

@Serializable
internal enum class ExpirationAction {
    /** Unknown action. */
    @SerialName("unknown_action") UNKNOWN,

    /** The version is deleted once it expires. */
    @SerialName("delete") DELETE,

    /** The version is disabled once it expires. */
    @SerialName("disable") DISABLE
}

/**
 * The response returned from https://www.scaleway.com/en/developers/api/secret-manager/#path-secrets-list-secrets.
 */
@Serializable
internal data class ScalewaySecret(
    /** ID of the secret. (UUID format) */
    val id: String,

    /** ID of the Project containing the secret. (UUID format) */
    val projectId: String,

    /** Name of the secret. */
    val name: String,

    /** Current status of the secret. */
    val status: SecretStatus,

    /** Date and time of the secret's creation. (RFC 3339 format) */
    val createdAt: String,

    /** Last update of the secret. (RFC 3339 format) */
    val updatedAt: String,

    /** List of the secret's tags. */
    val tags: List<String>,

    /** Number of versions for this secret. */
    val versionCount: Int,

    /** Updated description of the secret. */
    val description: String,

    /** True for secrets that are managed by another product. */
    val managed: Boolean,

    /** True for protected secrets that cannot be deleted. */
    val protected: Boolean,

    /** Type of the secret. */
    val type: SecretType,

    /** Location of the secret in the directory structure. */
    val path: String,

    /** List of Scaleway resources that can access and manage the secret. */
    val usedBy: List<String>,

    /** Returns the time at which deletion was requested. (RFC 3339 format) */
    val deletionRequestedAt: String?,

    /** Policy that defines whether / when a secret's version expires. Applies to all the versions by default. */
    val ephemeralPolicy: EphemeralPolicy?,

    /** Region of the secret. */
    val region: ScalewayRegion
)

@Serializable
internal enum class SecretStatus {
    /** Unknown status. */
    @SerialName("unknown_status") UNKNOWN,

    /** The secret can be read, modified and deleted. */
    @SerialName("ready") READY,

    /** No action can be performed on the secret. This status can only be applied and removed by Scaleway. */
    @SerialName("locked") LOCKED
}

/**
 * The response returned from https://www.scaleway.com/en/developers/api/secret-manager/#path-secret-versions-access-a-secrets-version-using-the-secrets-name-and-path.
 */
@Serializable
internal data class SecretsAccessResponse(
    /** ID of the secret. (UUID format) */
    val secretId: String,

    /** Version number. The first version of the secret is numbered 1, and all subsequent revisions augment by 1. */
    val revision: Int,

    /** The base64-encoded secret payload of the version. */
    val data: String,

    /**
     * The CRC32 checksum of the data as a base-10 integer. This field is only available if a CRC32 was supplied during
     * the creation of the version.
     */
    val dataCrc32: Int? = null,

    /** Type of the secret. */
    val type: SecretType = SecretType.UNKNOWN
)

/**
 * The request to use with https://www.scaleway.com/en/developers/api/secret-manager/#path-secrets-create-a-secret.
 */
@Serializable
internal data class VersionCreateRequest(
    /** The base64-encoded secret payload of the version. */
    val data: String,

    /**
     * The CRC32 checksum of the data as a base-10 integer. If specified, the Secret Manager will verify the integrity
     * of the data received against the given CRC32 checksum. An error is returned if the CRC32 does not match. If,
     * however, the CRC32 matches, it will be stored and returned along with the version on future access requests.
     */
    val dataCrc32: Int? = null,

    /** Description of the version. */
    val description: String? = null,

    /**
     * Disable the previous secret version. If there is no previous version or if the previous version was already
     * disabled, does nothing.
     */
    val disablePrevious: Boolean? = null
)

@Serializable
internal data class VersionCreateResponse(
    /** Version number. The first version of the secret is numbered 1, and all subsequent revisions augment by 1. */
    val revision: Int,

    /** ID of the secret. (UUID format) */
    val secretId: String,

    /** True if the version is the latest. */
    val latest: Boolean,

    /** Current status of the secret. */
    val status: VersionStatus = VersionStatus.UNKNOWN,

    /** Date and time of the secret's creation. (RFC 3339 format) */
    val createdAt: String? = null,

    /** Last update of the secret. (RFC 3339 format) */
    val updatedAt: String? = null,

    /** Description of the version. */
    val description: String? = null,

    /** Properties of the ephemeral version. */
    val ephemeralProperties: EphemeralProperties? = null
)

@Serializable
internal enum class VersionStatus {
    /** The version is in an invalid state. */
    @SerialName("unknown_status") UNKNOWN,

    /** The version is accessible. */
    @SerialName("enabled") ENABLED,

    /** The version is not accessible but can be enabled. */
    @SerialName("disabled") DISABLED,

    /** The version is permanently deleted. It is not possible to recover it. */
    @SerialName("deleted") DELETED
}

@Serializable
internal data class EphemeralProperties(
    /** The version's expiration date. If not specified, the version does not have an expiration date. */
    val expiresAt: String? = null,

    /** Set to true if the version expires after a single user access. */
    val expiresOnceAccessed: Boolean? = null,

    /** The action to perform when a version of the secret expires. */
    val action: ExpirationAction
)
