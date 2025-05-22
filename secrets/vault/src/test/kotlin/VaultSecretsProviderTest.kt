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

package org.eclipse.apoapsis.ortserver.secrets.vault

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.ktor.client.plugins.ClientRequestException

import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.Secret

class VaultSecretsProviderTest : WordSpec() {
    private val vault = installVaultTestContainer()

    init {
        "readSecret" should {
            "return the value of an existing secret" {
                val provider = vault.createProvider()

                val password = provider.readSecret(Path("password"))

                password shouldBe Secret("tiger")
            }

            "return null for a non-existing secret" {
                val provider = vault.createProvider()

                val result = provider.readSecret(Path("non-existing"))

                result should beNull()
            }

            "return null for a secret without the default key" {
                val provider = vault.createProvider()

                val result = provider.readSecret(Path("strange"))

                result should beNull()
            }

            "throw an exception for a failed request" {
                val provider = vault.createProvider("/secret/data/forbidden/path")

                shouldThrow<ClientRequestException> {
                    provider.readSecret(Path("password"))
                }
            }
        }

        "writeSecret" should {
            "create a new secret" {
                val newSecretPath = Path("brandNewSecret")
                val newSecretValue = Secret("You will never know...")
                val provider = vault.createProvider()

                provider.writeSecret(newSecretPath, newSecretValue)

                provider.readSecret(newSecretPath) shouldBe newSecretValue
            }

            "update an existing secret" {
                val newSecretPath = Path("secretWithUpdates")
                val firstValue = Secret("You will never know...")
                val secondValue = Secret("Maybe time after time?")
                val provider = vault.createProvider()
                provider.writeSecret(newSecretPath, firstValue)

                provider.writeSecret(newSecretPath, secondValue)

                provider.readSecret(newSecretPath) shouldBe secondValue
            }
        }

        "removeSecret" should {
            "remove an existing secret" {
                val targetPath = Path("justWaste")
                val provider = vault.createProvider()
                provider.writeSecret(targetPath, Secret("toBeDeleted"))

                provider.removeSecret(targetPath)

                provider.readSecret(targetPath) should beNull()
            }

            "remove a secret with all its versions" {
                val targetPath = Path("evenMoreWaste")
                val provider = vault.createProvider()
                provider.writeSecret(targetPath, Secret("toBeOverwritten"))
                provider.writeSecret(targetPath, Secret("toBeOverwrittenAgain"))
                provider.writeSecret(targetPath, Secret("toBeDeleted"))

                provider.removeSecret(targetPath)

                provider.readSecret(targetPath) should beNull()
            }
        }

        "createPath" should {
            "generate a path for an organization secret" {
                val provider = vault.createProvider()
                val result = provider.createPath(OrganizationId(1), "newSecret")

                result shouldBe Path("organization_1_newSecret")
            }

            "generate a path for a product secret" {
                val provider = vault.createProvider()
                val result = provider.createPath(ProductId(1), "newSecret")

                result shouldBe Path("product_1_newSecret")
            }

            "generate a path for a repository secret" {
                val provider = vault.createProvider()
                val result = provider.createPath(RepositoryId(1), "newSecret")

                result shouldBe Path("repository_1_newSecret")
            }
        }
    }
}
