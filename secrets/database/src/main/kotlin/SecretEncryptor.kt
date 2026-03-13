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

package org.eclipse.apoapsis.ortserver.secrets.database

/** Definition of an interface for encrypting and decrypting secret values stored by the database secrets provider. */
internal interface SecretEncryptor {
    /** Encrypt [plaintext] and return its stored representation. */
    fun encrypt(plaintext: String): StoredSecret

    /** Decrypt the given [secret] and return its plaintext value. */
    fun decrypt(secret: StoredSecret): String
}

/** The stored form of an encrypted secret. */
internal data class StoredSecret(
    /** The encrypted secret value in the format produced by the active encryptor implementation. */
    val encryptedValue: String,

    /** The identifier of the encryption scheme that was used to produce [encryptedValue]. */
    val encryptionScheme: String,

    /** The version of the configured key material that was used for encryption. */
    val keyVersion: Int
)
