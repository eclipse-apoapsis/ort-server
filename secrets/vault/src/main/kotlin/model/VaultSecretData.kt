/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.secrets.vault.model

import kotlinx.serialization.Serializable

/**
 * A data class representing the data stored for a secret in Vault.
 *
 * Vault allows an arbitrary number of key value pairs to be stored in a secret. The secrets abstraction, in contrast,
 * expects a single value to be stored at a specific path. To handle this discrepancy, the Vault implementation creates
 * secrets under the specified paths with a default key containing the value.
 *
 * This class defines the generic structure of a Vault secret, but offers some convenient functions to set and query
 * the value of the default key.
 */
@Serializable
data class VaultSecretData(
    /** The map with the sub keys and their values assigned to a secret.*/
    val data: Map<String, String>
) {
    companion object {
        /**
         * The name of the key under which the value provided by the secrets abstraction implementation is stored.
         */
        private const val DEFAULT_KEY = "value"

        /**
         * Return a [VaultSecretData] instance that stores the given [value] under the default key. This instance can
         * then be used to write a secret.
         */
        fun withValue(value: String): VaultSecretData = VaultSecretData(mapOf(DEFAULT_KEY to value))
    }

    /** The default value of the secret represented by this instance. */
    val value: String?
        get() = data[DEFAULT_KEY]
}

@Serializable
data class VaultSecretResponse(
    val data: VaultSecretData
)
