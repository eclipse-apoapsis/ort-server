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

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.fail
import io.kotest.common.runBlocking
import io.kotest.core.extensions.install
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.ContainerExtension

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

import java.util.regex.Pattern

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.secrets.vault.model.VaultCredentials

import org.testcontainers.utility.DockerImageName
import org.testcontainers.vault.VaultContainer

class VaultTestContainer {
    companion object {
        /** The root path under which secrets are stored in the vault. */
        const val PATH = "ort/server/secrets/"

        /** The root token used by the vault service. */
        private const val VAULT_TOKEN = "onetobindthemall"

        /** The header in which vault expects the authorization token. */
        private const val TOKEN_HEADER = "X-Vault-Token"

        /** The name of the role used to authenticate the client. */
        private const val ROLE_NAME = "test-server"

        /** The name of the docker image for the vault test container. */
        private val image = DockerImageName.parse("hashicorp/vault:1.13").asCompatibleSubstituteFor("vault")

        /**
         * Obtain the [VaultCredentials] to construct an authorized token for the test Vault container. Use [client]
         * for requests against the API.
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

            // Create a policy for the role used within tests that allows full access to the secrets below the test
            // path.
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
         * Extract the value of the string field with the given [name] from this JSON string or fail if this field
         * cannot be resolved.
         */
        private fun String.extractJsonField(name: String): String {
            val regExtract = Regex(""""${Pattern.quote(name)}"\s*:\s*"([^"]+)"""")
            return regExtract.find(this)?.groupValues?.get(1) ?: fail("Cannot find field '$name' in '$this'.")
        }
    }

    /** The managed [VaultContainer]. */
    val vault: VaultContainer<*> = VaultContainer(image)
        .withVaultToken(VAULT_TOKEN)
        .withSecret("secret/${PATH}user", "value=scott")
        .withSecret("secret/${PATH}password", "value=tiger")
        .withSecret("secret/${PATH}strange", "noValue=set")

    /** The credentials for accessing the container. */
    private val credentials: VaultCredentials by lazy { setUpCredentials() }

    /**
     * Create a [VaultSecretsProvider] for accessing the managed Vault service under the given [rootPath].
     */
    fun createProvider(rootPath: String = PATH): VaultSecretsProvider {
        val config = VaultConfiguration(vault.httpHostAddress, credentials, rootPath)
        return VaultSecretsProvider(config)
    }

    /**
     * Create a [ConfigManager] that can be used to obtain a secret storage backed by the managed Vault container.
     */
    fun createApplicationConfig(): ConfigManager {
        val vaultProperties = mapOf(
            "name" to "vault",
            "vaultUri" to vault.httpHostAddress,
            "vaultRootPath" to PATH
        )
        val secretProperties = mapOf(
            "vaultRoleId" to credentials.roleId,
            "vaultSecretId" to credentials.secretId
        )
        val configManagerProperties = mapOf(
            ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
            ConfigSecretProviderFactoryForTesting.SECRETS_PROPERTY to secretProperties
        )
        val configMap = mapOf(
            "secretsProvider" to vaultProperties,
            ConfigManager.CONFIG_MANAGER_SECTION to configManagerProperties
        )

        return ConfigManager.create(ConfigFactory.parseMap(configMap))
    }

    /**
     * Trigger the configuration of the managed Vault service. Set up the AppRole authentication method and obtain the
     * corresponding credentials.
     */
    private fun setUpCredentials(): VaultCredentials {
        val client = createVaultClient(vault)

        return runBlocking {
            configureAppRoleAuthentication(client)
            getCredentials(client)
        }
    }
}

/**
 * Create a [VaultTestContainer] instance and install it as extension in this [Spec].
 */
fun Spec.installVaultTestContainer(): VaultTestContainer {
    val container = VaultTestContainer()
    install(ContainerExtension(container.vault))

    return container
}

/**
 * Add a [secret] ath [path] to the [VaultContainer]. The [secret] must be in the form `secret=value`.
 */
private fun <SELF : VaultContainer<SELF>> VaultContainer<SELF>.withSecret(
    path: String,
    secret: String
): VaultContainer<SELF> =
    withInitCommand("kv put $path $secret")
