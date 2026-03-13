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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretValue

class DatabaseSecretsProviderFactoryTest : WordSpec({
    extension(DatabaseTestExtension())

    "SecretStorage.createStorage" should {
        "create a SecretStorage accessing the database provider" {
            val storage = createStorage()
            val path = Path("organization_1_token")

            storage.writeSecret(path, SecretValue("secret"))

            storage.getSecret(path) shouldBe SecretValue("secret")
        }

        "reject a master password with leading or trailing whitespace" {
            val exception = shouldThrow<ConfigException> {
                createStorage(
                    secretsProviderConfig(
                        masterPassword = "  thisIsAStrongPassword  "
                    )
                )
            }

            exception.message shouldBe
                    "The database secrets master password must not have leading or trailing whitespace."
        }

        "reject a blank master password" {
            val exception = shouldThrow<ConfigException> {
                createStorage(secretsProviderConfig(masterPassword = ""))
            }

            exception.message shouldBe "The database secrets master password must not be blank."
        }

        "reject a master password that is too short" {
            val exception = shouldThrow<ConfigException> {
                createStorage(secretsProviderConfig(masterPassword = "too-short"))
            }

            exception.message shouldBe "The database secrets master password must be at least 16 characters long."
        }

        "reject a salt with leading or trailing whitespace" {
            val exception = shouldThrow<ConfigException> {
                createStorage(
                    secretsProviderConfig(
                        salt = " deadbeefcafebabedeadbeefcafebabe "
                    )
                )
            }

            exception.message shouldBe "The database secrets salt must not have leading or trailing whitespace."
        }

        "reject a blank salt" {
            val exception = shouldThrow<ConfigException> {
                createStorage(secretsProviderConfig(salt = ""))
            }

            exception.message shouldBe "The database secrets salt must not be blank."
        }

        "reject a salt that is not hex-encoded" {
            val exception = shouldThrow<ConfigException> {
                createStorage(secretsProviderConfig(salt = "deadbeefcafebabedeadbeefcafebabeg"))
            }

            exception.message shouldBe "The database secrets salt must be a hex-encoded string."
        }

        "reject a salt with an odd number of hex characters" {
            val exception = shouldThrow<ConfigException> {
                createStorage(secretsProviderConfig(salt = "deadbeefcafebabedeadbeefcafebab"))
            }

            exception.message shouldBe "The database secrets salt must contain an even number of hex characters."
        }

        "reject a salt that is too short" {
            val exception = shouldThrow<ConfigException> {
                createStorage(secretsProviderConfig(salt = "deadbeefcafebabe"))
            }

            exception.message shouldBe "The database secrets salt must be at least 16 bytes (32 hex characters) long."
        }

        "reject a non-positive key version" {
            val exception = shouldThrow<ConfigException> {
                createStorage(secretsProviderConfig(keyVersion = "0"))
            }

            exception.message shouldBe "The database secrets key version must be a positive integer."
        }
    }
})
