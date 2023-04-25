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

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.server.secrets.Path
import org.ossreviewtoolkit.server.secrets.Secret
import org.ossreviewtoolkit.server.secrets.SecretsProvider
import org.ossreviewtoolkit.server.secrets.vault.model.VaultLoginResponse
import org.ossreviewtoolkit.server.secrets.vault.model.VaultSecretData
import org.ossreviewtoolkit.server.secrets.vault.model.VaultSecretResponse

/** The header in which vault expects the authorization token. */
private const val TOKEN_HEADER = "X-Vault-Token"

/** The header to specify the Vault namespace. */
private const val NAMESPACE_HEADER = "X-Vault-Namespace"

/** The URL prefix used by most requests to access secrets. */
private const val SECRET_ACCESS_PREFIX = "data"

/** The URL prefix to delete all the versions of a secret. */
private const val SECRET_DELETE_PREFIX = "metadata"

/**
 * An implementation of the [SecretsProvider] interface based on HashiCorp Vault [1].
 *
 * This implementation allows accessing secrets managed via the KV Secrets Engine Version 2 [2]. Authentication is
 * done via the AppRole method [3].
 *
 * [1] https://developer.hashicorp.com/vault.
 * [2] https://developer.hashicorp.com/vault/api-docs/secret/kv/kv-v2
 * [3] https://developer.hashicorp.com/vault/api-docs/auth/approle
 */
class VaultSecretsProvider(
    private val config: VaultConfiguration
) : SecretsProvider {
    /** The client to interact with the Vault service. */
    private val vaultClient = createAuthorizedClient()

    override fun readSecret(path: Path): Secret? {
        return vaultRequest {
            val response = get(path.toUri()) {
                // Override this flag here to handle 404 responses manually.
                expectSuccess = false
            }

            when {
                response.status == HttpStatusCode.NotFound -> null
                response.status.isSuccess() -> {
                    val secretResponse: VaultSecretResponse = response.body()
                    secretResponse.data.value?.let(::Secret)
                }

                else -> throw ClientRequestException(response, response.body())
            }
        }
    }

    override fun writeSecret(path: Path, secret: Secret) {
        val data = VaultSecretData.withValue(secret.value)
        vaultRequest {
            post(path.toUri()) {
                setBody(data)
            }
        }
    }

    override fun removeSecret(path: Path) {
        vaultRequest {
            delete(path.toUri(SECRET_DELETE_PREFIX))
        }
    }

    override fun createPath(organizationId: Long?, productId: Long?, repositoryId: Long?, secretName: String): Path {
        val secretType = when {
            organizationId != null -> "organization"
            productId != null -> "product"
            repositoryId != null -> "repository"
            else -> throw IllegalArgumentException(
                "Either one of organizationId, productId or repositoryId should be specified to create a path."
            )
        }
        return Path(listOfNotNull(secretType, organizationId, productId, repositoryId, secretName).joinToString("_"))
    }

    /**
     * Create an [HttpClient] that can be used for sending API requests against the Vault service. The client is
     * configured to provide the required authentication headers.
     */
    private fun createAuthorizedClient(): HttpClient = runBlocking {
        createClient(null).use { client ->
            val loginResponse: VaultLoginResponse = client.post("/v1/auth/approle/login") {
                setBody(config.credentials)
            }.body()

            createClient(loginResponse.auth.clientToken)
        }
    }

    /**
     * Create an [HttpClient] with a configuration to communicate with the Vault service. If an
     * [authorization token][authToken] is already known, the client is prepared to add a corresponding header for all
     * requests.
     */
    private fun createClient(authToken: String?): HttpClient =
        HttpClient(OkHttp) {
            defaultRequest {
                url(config.vaultUri)
                header(TOKEN_HEADER, authToken)
                config.namespace?.let { header(NAMESPACE_HEADER, it) }
                contentType(ContentType.Application.Json)
            }

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
            }

            expectSuccess = true
        }

    /**
     * Execute a request defined by the given [block] using the configured HTTP client. This is a convenience
     * function that bridges between the blocking API of [SecretsProvider] and the non-blocking API of the client.
     */
    private fun <T> vaultRequest(block: suspend HttpClient.() -> T): T = runBlocking {
        vaultClient.block()
    }

    /**
     * Convert this [Path] to a URI for accessing the corresponding secret. Take the configuration settings
     * and the given [operation] into account.
     */
    private fun Path.toUri(operation: String = SECRET_ACCESS_PREFIX): String =
        "/v1/${config.prefix}/$operation/${config.rootPath}$path"
}
