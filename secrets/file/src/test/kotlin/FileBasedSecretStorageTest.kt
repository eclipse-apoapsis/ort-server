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

package org.eclipse.apoapsis.ortserver.secrets

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe

import java.io.File

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage.Companion.CONFIG_PREFIX
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage.Companion.NAME_PROPERTY
import org.eclipse.apoapsis.ortserver.secrets.file.FileBasedSecretsProvider
import org.eclipse.apoapsis.ortserver.secrets.file.FileBasedSecretsProvider.Companion.PATH_PROPERTY
import org.eclipse.apoapsis.ortserver.secrets.file.model.FileBasedSecretsStorage

class FileBasedSecretStorageTest : WordSpec() {
    private val storageFile = tempfile()
    private val storage = getStorage(storageFile)

    init {
        beforeEach {
            initStorage(storageFile)
        }

        "readSecret" should {
            "return the value of an existing secret" {
                val password = storage.readSecret(Path("password"))

                password shouldBe Secret("securePassword123")
            }

            "return null for a non-existing secret" {
                val result = storage.readSecret(Path("non-existing"))

                result shouldBe null
            }
        }

        "writeSecret" should {
            "create a new secret" {
                val newSecretPath = Path("brandNewSecret")
                val newSecretValue = Secret("You will never know...")

                storage.writeSecret(newSecretPath, newSecretValue)

                storage.readSecret(newSecretPath) shouldBe newSecretValue
            }

            "update an existing secret" {
                val newSecretPath = Path("secretWithUpdates")
                val firstValue = Secret("You will never know...")
                val secondValue = Secret("Maybe time after time?")

                storage.writeSecret(newSecretPath, firstValue)

                storage.writeSecret(newSecretPath, secondValue)

                storage.readSecret(newSecretPath) shouldBe secondValue
            }
        }

        "removeSecret" should {
            "remove an existing secret" {
                val targetPath = Path("justWaste")

                storage.writeSecret(targetPath, Secret("toBeDeleted"))

                storage.removeSecret(targetPath)

                storage.readSecret(targetPath) shouldBe null
            }

            "remove a secret with all its versions" {
                val targetPath = Path("evenMoreWaste")

                storage.writeSecret(targetPath, Secret("toBeOverwritten"))
                storage.writeSecret(targetPath, Secret("toBeOverwrittenAgain"))
                storage.writeSecret(targetPath, Secret("toBeDeleted"))

                storage.removeSecret(targetPath)

                storage.readSecret(targetPath) shouldBe null
            }
        }

        "createPath" should {
            "generate a path for an organization secret" {
                val result = storage.createPath(OrganizationId(1), "newSecret")

                result shouldBe Path("organization_1_newSecret")
            }

            "generate a path for a product secret" {
                val result = storage.createPath(ProductId(1), "newSecret")

                result shouldBe Path("product_1_newSecret")
            }

            "generate a path for a repository secret" {
                val result = storage.createPath(RepositoryId(1), "newSecret")

                result shouldBe Path("repository_1_newSecret")
            }
        }
    }
}

/**
 * Returns a config for [FileBasedSecretsProvider].
 */
private fun getStorage(storageFile: File): SecretStorage {
    val properties = mapOf(
        "$CONFIG_PREFIX.$NAME_PROPERTY" to FileBasedSecretsProvider.NAME,
        "$CONFIG_PREFIX.$PATH_PROPERTY" to storageFile.canonicalPath
    )
    return SecretStorage.createStorage(ConfigManager.create(ConfigFactory.parseMap(properties)))
}

@OptIn(ExperimentalEncodingApi::class)
private fun initStorage(storageFile: File) {
    val serializer = FileBasedSecretsStorage.serializer()
    val json = Json {
        allowStructuredMapKeys = true
    }

    val secretsJson = json.encodeToString(
        serializer,
        FileBasedSecretsStorage(mapOf("password" to "securePassword123").toMutableMap())
    )

    val encryptedSecrets = Base64.encode(secretsJson.toByteArray())
    storageFile.writeText(encryptedSecrets)
}
