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

import org.springframework.security.crypto.encrypt.Encryptors

private const val ENCRYPTION_SCHEME = "spring-v1"

/** A [SecretEncryptor] based on Spring Security Crypto. */
internal class SpringCryptoSecretEncryptor(
    password: String,
    salt: String,
    private val keyVersion: Int
) : SecretEncryptor {
    private val encryptor = Encryptors.delux(password, salt)

    override fun encrypt(plaintext: String): StoredSecret =
        StoredSecret(
            encryptedValue = encryptor.encrypt(plaintext),
            encryptionScheme = ENCRYPTION_SCHEME,
            keyVersion = keyVersion
        )

    override fun decrypt(secret: StoredSecret): String {
        require(secret.encryptionScheme == ENCRYPTION_SCHEME) {
            "Unsupported encryption scheme '${secret.encryptionScheme}'."
        }
        require(secret.keyVersion == keyVersion) {
            "Unsupported key version '${secret.keyVersion}'."
        }

        return encryptor.decrypt(secret.encryptedValue)
    }
}
