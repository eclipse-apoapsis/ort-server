/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.secrets.vault

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.WordSpec
import io.kotest.extensions.testcontainers.TestContainerExtension
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

import java.util.regex.Pattern

import org.ossreviewtoolkit.server.secrets.Path
import org.ossreviewtoolkit.server.secrets.Secret
import org.ossreviewtoolkit.server.secrets.vault.model.VaultCredentials

import org.testcontainers.utility.DockerImageName
import org.testcontainers.vault.VaultContainer

/** The root token used by the vault service. */
private const val VAULT_TOKEN = "onetobindthemall"

/** The header in which vault expects the authorization token. */
private const val TOKEN_HEADER = "X-Vault-Token"

/** The name of the role used to authenticate the client. */
private const val ROLE_NAME = "test-server"

/** The root path under which secrets are stored in the vault. */
private const val PATH = "ort/server/secrets/"

/** The name of the docker image for the vault test container. */
private val image = DockerImageName.parse("hashicorp/vault:1.13").asCompatibleSubstituteFor("vault")

class VaultSecretsProviderTest : WordSpec() {
    /** The credentials to access the Vault test container. */
    private lateinit var credentials: VaultCredentials

    init {
        val vault = VaultContainer(image)
            .withVaultToken(VAULT_TOKEN)
            .withSecretInVault("secret/${PATH}user", "value=scott")
            .withSecretInVault("secret/${PATH}password", "value=tiger")
            .withSecretInVault("secret/${PATH}strange", "noValue=set")

        install(TestContainerExtension(vault))

        beforeSpec {
            val client = createVaultClient(vault)

            configureAppRoleAuthentication(client)

            credentials = getCredentials(client)
        }

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
    }

    /**
     * Create a [VaultConfiguration] for accessing this [VaultContainer] under the given [rootPath].
     */
    private fun VaultContainer<*>.createConfig(rootPath: String): VaultConfiguration =
        VaultConfiguration(httpHostAddress, credentials, rootPath)

    /**
     * Create a [VaultSecretsProvider] for accessing this [VaultContainer] under the given [rootPath].
     */
    private fun VaultContainer<*>.createProvider(rootPath: String = PATH): VaultSecretsProvider =
        VaultSecretsProvider(createConfig(rootPath))
}

/**
 * Obtain the [VaultCredentials] to construct an authorized token for the test Vault container. Use [client] for
 * requests against the API.
 */
private suspend fun getCredentials(client: HttpClient): VaultCredentials {
    // Query the roleId.
    val roleId = client.get("/v1/auth/approle/role/$ROLE_NAME/role-id")
        .body<String>().extractJsonField("role_id")

    // Generate and obtain a secretId.
    val secretId = client.post("/v1/auth/approle/role/$ROLE_NAME/secret-id")
        .body<String>().extractJsonField("secret_id")

    return VaultCredentials(roleId, secretId)
}

/**
 * Prepare the Vault container to support AppRole authentication and set up the test role with an associated
 * policy. Use [client] to interact with the container.
 */
private suspend fun configureAppRoleAuthentication(client: HttpClient) {
    // Enable AppRole authentication.
    client.post("/v1/sys/auth/approle") {
        setBody("""{"type": "approle"}""")
    }

    // Create a policy for the role used within tests that allows full access to the secrets below the test path.
    val policyAccess = policy("secret/data/$PATH", "create", "read", "update", "delete")
    val policyDelete = policy("secret/metadata/$PATH", "delete")
    client.put("/v1/sys/policies/acl/$ROLE_NAME") {
        setBody(
            """{
                 "policy": "$policyAccess\n$policyDelete" 
            }
            """.trimIndent()
        )
    }

    // Create a test role and associate it with the policy.
    client.post("/v1/auth/approle/role/$ROLE_NAME") {
        setBody(
            """
                {
                  "token_policies": "$ROLE_NAME",
                  "token_ttl": "2h"
                }
            """.trimIndent()
        )
    }
}

/**
 * Generate the string to define a policy that grants the given [capabilities] to the given [path].
 */
private fun policy(path: String, vararg capabilities: String): String =
    """path \"$path*\" {\n capabilities = [ ${capabilities.joinToString(", ") { """\"$it\"""" }} ]\n}"""

/**
 * Create an [HttpClient] that is configured for the interaction with the given [vault] container.
 */
private fun createVaultClient(vault: VaultContainer<*>): HttpClient =
    HttpClient(OkHttp) {
        defaultRequest {
            url(vault.httpHostAddress)
            header(TOKEN_HEADER, VAULT_TOKEN)
        }
        expectSuccess = true
    }

/**
 * Extract the value of the string field with the given [name] from this JSON string or fail if this field cannot
 * be resolved.
 */
private fun String.extractJsonField(name: String): String {
    val regExtract = Regex(""""${Pattern.quote(name)}"\s*:\s*"([^"]+)"""")
    return regExtract.find(this)?.groupValues?.get(1) ?: fail("Cannot find field '$name' in '$this'.")
}
