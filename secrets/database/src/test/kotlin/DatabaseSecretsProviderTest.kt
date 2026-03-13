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

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretValue

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

class DatabaseSecretsProviderTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    "readSecret" should {
        "read an existing secret via SecretStorage" {
            val storage = createStorage()
            val path = Path("organization_1_apiToken")
            val secret = SecretValue("top-secret")

            storage.writeSecret(path, secret)

            storage.readSecret(path) shouldBe secret
        }

        "return null for a missing secret" {
            val storage = createStorage()

            storage.readSecret(Path("organization_1_apiToken")) should beNull()
        }
    }

    "writeSecret" should {
        "overwrite an existing secret" {
            val storage = createStorage()
            val path = Path("organization_1_apiToken")

            storage.writeSecret(path, SecretValue("first"))
            storage.writeSecret(path, SecretValue("second"))

            storage.readSecret(path) shouldBe SecretValue("second")
        }

        "persist encrypted values instead of plaintext" {
            val storage = createStorage()
            val path = Path("organization_1_apiToken")
            val plaintext = "top-secret"

            storage.writeSecret(path, SecretValue(plaintext))

            val row = dbExtension.db.blockingQuery {
                DatabaseSecretsTable.selectAll().where { DatabaseSecretsTable.path eq path.path }.single()
            }

            row[DatabaseSecretsTable.encryptionScheme] shouldBe "spring-v1"
            row[DatabaseSecretsTable.keyVersion] shouldBe 1
            row[DatabaseSecretsTable.encryptedValue] shouldNotContain plaintext
        }

        "preserve createdAt when overwriting an existing secret" {
            val storage = createStorage()
            val path = Path("organization_1_apiToken")

            storage.writeSecret(path, SecretValue("first"))

            val firstRow = dbExtension.db.blockingQuery {
                DatabaseSecretsTable.select(
                    DatabaseSecretsTable.createdAt,
                    DatabaseSecretsTable.updatedAt
                ).where { DatabaseSecretsTable.path eq path.path }.single()
            }

            storage.writeSecret(path, SecretValue("second"))

            val secondRow = dbExtension.db.blockingQuery {
                DatabaseSecretsTable.select(
                    DatabaseSecretsTable.createdAt,
                    DatabaseSecretsTable.updatedAt
                ).where { DatabaseSecretsTable.path eq path.path }.single()
            }

            secondRow[DatabaseSecretsTable.createdAt] shouldBe firstRow[DatabaseSecretsTable.createdAt]
            secondRow[DatabaseSecretsTable.updatedAt] shouldNotBe firstRow[DatabaseSecretsTable.updatedAt]
        }

        "re-encrypt the value if the same secret is saved twice" {
            val storage = createStorage()
            val path = Path("organization_1_apiToken")
            val plaintext = "top-secret"

            storage.writeSecret(path, SecretValue(plaintext))

            val firstRow = dbExtension.db.blockingQuery {
                DatabaseSecretsTable.selectAll().where { DatabaseSecretsTable.path eq path.path }.single()
            }

            storage.writeSecret(path, SecretValue(plaintext))

            val secondRow = dbExtension.db.blockingQuery {
                DatabaseSecretsTable.selectAll().where { DatabaseSecretsTable.path eq path.path }.single()
            }

            firstRow[DatabaseSecretsTable.encryptedValue] shouldNotBe secondRow[DatabaseSecretsTable.encryptedValue]
        }
    }

    "removeSecret" should {
        "remove an existing secret" {
            val storage = createStorage()
            val path = Path("organization_1_apiToken")

            storage.writeSecret(path, SecretValue("temp"))
            storage.removeSecret(path)

            storage.readSecret(path) should beNull()
        }
    }
})

internal fun createStorage(configMap: Map<String, String> = secretsProviderConfig()): SecretStorage {
        val config = ConfigFactory.parseMap(configMap)

        return SecretStorage.createStorage(ConfigManager.create(config))
}

internal fun secretsProviderConfig(
    masterPassword: String = "thisIsAStrongPassword",
    salt: String = "deadbeefcafebabedeadbeefcafebabe",
    keyVersion: String = "1"
) =
    mapOf(
        "secretsProvider.name" to "database",
        "secretsProvider.databaseMasterPassword" to masterPassword,
        "secretsProvider.databaseSalt" to salt,
        "secretsProvider.databaseKeyVersion" to keyVersion
    )
