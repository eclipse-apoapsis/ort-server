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

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretValue
import org.eclipse.apoapsis.ortserver.secrets.SecretsProvider

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

/** A [SecretsProvider] implementation that stores encrypted secrets in the database. */
internal class DatabaseSecretsProvider(
    private val encryptor: SecretEncryptor
) : SecretsProvider {
    override fun readSecret(path: Path): SecretValue? = transaction {
        DatabaseSecretsTable.select(
            DatabaseSecretsTable.encryptedValue,
            DatabaseSecretsTable.encryptionScheme,
            DatabaseSecretsTable.keyVersion
        ).where {
            DatabaseSecretsTable.path eq path.path
        }.singleOrNull()?.let { row ->
            SecretValue(
                encryptor.decrypt(
                    StoredSecret(
                        encryptedValue = row[DatabaseSecretsTable.encryptedValue],
                        encryptionScheme = row[DatabaseSecretsTable.encryptionScheme],
                        keyVersion = row[DatabaseSecretsTable.keyVersion]
                    )
                )
            )
        }
    }

    override fun writeSecret(path: Path, secret: SecretValue) {
        val encryptedSecret = encryptor.encrypt(secret.value)

        transaction {
            DatabaseSecretsTable.upsert(DatabaseSecretsTable.path) {
                it[DatabaseSecretsTable.path] = path.path
                it[encryptedValue] = encryptedSecret.encryptedValue
                it[encryptionScheme] = encryptedSecret.encryptionScheme
                it[keyVersion] = encryptedSecret.keyVersion
                it[updatedAt] = Clock.System.now()
            }
        }
    }

    override fun removeSecret(path: Path) {
        transaction {
            DatabaseSecretsTable.deleteWhere { DatabaseSecretsTable.path eq path.path }
        }
    }
}
