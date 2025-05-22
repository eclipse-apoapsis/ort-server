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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beInstanceOf

import kotlin.IllegalArgumentException

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting.Companion.PASSWORD_PATH
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting.Companion.PASSWORD_SECRET

class SecretStorageTest : WordSpec({
    "createStorage" should {
        "fail if the name property is not provided in the configuration" {
            val configManager = ConfigManager.create(ConfigFactory.empty())

            val exception = shouldThrow<SecretStorageException> {
                SecretStorage.createStorage(configManager)
            }

            exception.message shouldContain (SecretStorage.NAME_PROPERTY)
        }

        "fail if the configured SecretsProvider cannot be resolved" {
            val providerName = "nonExistingProvider"
            val configManager = ConfigManager.create(
                ConfigFactory.parseMap(
                    mapOf("${SecretStorage.CONFIG_PREFIX}.${SecretStorage.NAME_PROPERTY}" to providerName)
                )
            )

            val exception = shouldThrow<SecretStorageException> {
                SecretStorage.createStorage(configManager)
            }

            exception.message shouldContain providerName
        }
    }

    "readSecret" should {
        "return an existing secret" {
            createStorage().readSecret(PASSWORD_PATH) shouldBe PASSWORD_SECRET
        }

        "return null for a non-existing secret" {
            createStorage().readSecret(Path("unknown-path")) should beNull()
        }

        "wrap an exception thrown by the SecretsProvider" {
            val exception = shouldThrow<SecretStorageException> {
                createStorage().readSecret(ERROR_PATH)
            }

            exception.cause should beInstanceOf<IllegalArgumentException>()
        }
    }

    "getSecret" should {
        "return an existing secret" {
            createStorage().getSecret(PASSWORD_PATH) shouldBe PASSWORD_SECRET
        }

        "throw an exception for a non-existing secret" {
            val path = Path("non-existing")

            val exception = shouldThrow<SecretStorageException> {
                createStorage().getSecret(path)
            }

            exception.message shouldContain path.path
        }
    }

    "readSecretCatching" should {
        "return a Result with an existing secret" {
            val result = createStorage().readSecretCatching(PASSWORD_PATH)

            result shouldBeSuccess PASSWORD_SECRET
        }

        "return a Result with null for a non-existing secret" {
            val result = createStorage().readSecretCatching(Path("non-existing"))

            result shouldBeSuccess null
        }

        "return a failed Result if the SecretsProvider throws an exception" {
            val result = createStorage().readSecretCatching(ERROR_PATH)

            result shouldBeFailure { exception ->
                exception.cause should beInstanceOf<IllegalArgumentException>()
            }
        }
    }

    "getSecretCatching" should {
        "return a Result with an existing secret" {
            val result = createStorage().getSecretCatching(PASSWORD_PATH)

            result shouldBeSuccess PASSWORD_SECRET
        }

        "return a failed Result for a non existing secret" {
            val path = Path("unresolvable")

            val result = createStorage().getSecretCatching(path)

            result shouldBeFailure { exception ->
                exception should beInstanceOf<SecretStorageException>()
                exception.message shouldContain path.path
            }
        }
    }

    "writeSecret" should {
        "write a secret successfully" {
            val newPath = Path("new-secret")
            val newSecret = Secret("BrandNewSecret")
            val storage = createStorage()

            storage.writeSecret(newPath, newSecret)

            storage.getSecret(newPath) shouldBe newSecret
        }

        "throw an exception if writing fails" {
            val exception = shouldThrow<SecretStorageException> {
                createStorage().writeSecret(ERROR_PATH, Secret("will-fail"))
            }

            exception.cause should beInstanceOf<IllegalArgumentException>()
        }
    }

    "writeSecretCatching" should {
        "return a success result if the operation is successful" {
            val newPath = Path("new-secret")
            val newSecret = Secret("BrandNewSecret")
            val storage = createStorage()

            val result = storage.writeSecretCatching(newPath, newSecret)

            result.isSuccess shouldBe true
            storage.getSecret(newPath) shouldBe newSecret
        }

        "return a failure result for a failing operation" {
            val result = createStorage().writeSecretCatching(ERROR_PATH, Secret("?"))

            result shouldBeFailure { exception ->
                exception should beInstanceOf<SecretStorageException>()
                exception.cause should beInstanceOf<IllegalArgumentException>()
            }
        }
    }

    "removeSecret" should {
        "successfully remove a secret" {
            val storage = createStorage()

            storage.removeSecret(PASSWORD_PATH)

            storage.readSecret(PASSWORD_PATH) should beNull()
        }

        "throw an exception if removing fails" {
            val exception = shouldThrow<SecretStorageException> {
                createStorage().readSecret(ERROR_PATH)
            }

            exception.cause should beInstanceOf<IllegalArgumentException>()
        }
    }

    "removeSecretCatching" should {
        "return a success result if the operation is successful" {
            val storage = createStorage()
            val result = storage.removeSecretCatching(PASSWORD_PATH)

            result.isSuccess shouldBe true
            storage.readSecret(PASSWORD_PATH) should beNull()
        }

        "return a failure result for a failed operation" {
            val result = createStorage().removeSecretCatching(ERROR_PATH)

            result shouldBeFailure { exception ->
                exception should beInstanceOf<SecretStorageException>()
                exception.cause should beInstanceOf<IllegalArgumentException>()
            }
        }
    }

    "createPath" should {
        "generate a path for an organization secret" {
            val storage = createStorage()
            val result = storage.createPath(OrganizationId(1), "newSecret")

            result shouldBe Path("organization_1_newSecret")
        }

        "generate a path for a product secret" {
            val storage = createStorage()
            val result = storage.createPath(ProductId(1), "newSecret")

            result shouldBe Path("product_1_newSecret")
        }

        "generate a path for a repository secret" {
            val storage = createStorage()
            val result = storage.createPath(RepositoryId(1), "newSecret")

            result shouldBe Path("repository_1_newSecret")
        }
    }
})

/** The path that causes errors. */
private val ERROR_PATH = Path("error")

/**
 * Return a [SecretStorage] object wrapping the test provider.
 */
private fun createStorage(): SecretStorage {
    val properties = mapOf(
        "${SecretStorage.CONFIG_PREFIX}.${SecretStorage.NAME_PROPERTY}" to SecretsProviderFactoryForTesting.NAME,
        "${SecretStorage.CONFIG_PREFIX}.${SecretsProviderFactoryForTesting.ERROR_PATH_PROPERTY}" to ERROR_PATH.path
    )
    val configManager = ConfigManager.create(ConfigFactory.parseMap(properties))

    return SecretStorage.createStorage(configManager)
}
