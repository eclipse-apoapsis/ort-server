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

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.plugin
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.Secret
import org.eclipse.apoapsis.ortserver.secrets.SecretsProvider
import org.eclipse.apoapsis.ortserver.secrets.vault.model.VaultLoginResponse
import org.eclipse.apoapsis.ortserver.secrets.vault.model.VaultSecretData
import org.eclipse.apoapsis.ortserver.secrets.vault.model.VaultSecretResponse
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking

import org.slf4j.LoggerFactory

/** The header in which vault expects the authorization token. */
private const val TOKEN_HEADER = "X-Vault-Token"

/** The header to specify the Vault namespace. */
private const val NAMESPACE_HEADER = "X-Vault-Namespace"

/** The URL prefix used by most requests to access secrets. */
private const val SECRET_ACCESS_PREFIX = "data"

/** The URL prefix to delete all the versions of a secret. */
private const val SECRET_DELETE_PREFIX = "metadata"

/** The path to log into vault and obtain a token. */
private const val LOGIN_PATH = "/v1/auth/approle/login"

/**
 * An implementation of the [SecretsProvider] interface for [HashiCorp Vault](https://developer.hashicorp.com/vault).
 *
 * This implementation allows accessing secrets managed via the
 * [KV Secrets Engine Version 2](https://developer.hashicorp.com/vault/api-docs/secret/kv/kv-v2). Authentication is done
 * via the [AppRole method](https://developer.hashicorp.com/vault/api-docs/auth/approle).
 */
class VaultSecretsProvider(
    private val config: VaultConfiguration
) : SecretsProvider {
    companion object {
        private val logger = LoggerFactory.getLogger(VaultSecretsProvider::class.java)
    }

    /** The client to interact with the Vault service. */
    private val vaultClient = createClient()

    override fun readSecret(path: Path): Secret? {
        return vaultRequest {
            val response = get(path.toUri()) {
                // Override this flag here to handle 404 responses manually.
                expectSuccess = false
            }

            when {
                response.status == HttpStatusCode.NotFound -> null
                response.status.isSuccess() -> {
                    val secretResponse = response.body<VaultSecretResponse>()
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

    /**
     * Create an [HttpClient] with a configuration to communicate with the Vault service. The client is prepared to
     * obtain a new client token if necessary.
     */
    private fun createClient(): HttpClient {
        val client = HttpClient(OkHttp) {
            defaultRequest {
                url(config.vaultUri)
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

        configureTokenHandling(client)

        return client
    }

    /**
     * Configure the given [client] to request a new Vault token if necessary, i.e., if a request with the previous
     * token is rejected with a Forbidden status. Vault is a bit special in this regard; so it uses a proprietary
     * header for the token and does not return the default 401 Unauthorized status code. Therefore, the standard
     * bearer-authentication offered by KTor cannot be used here.
     */
    private fun configureTokenHandling(client: HttpClient) {
        // The token to authenticate against Vault. This is fetched and updated on demand. Since this data is shared
        // among all callers and can therefore be accessed concurrently, it needs to be guarded by a mutex.
        var token: String? = null
        val mutex = Mutex()

        client.plugin(HttpSend).intercept { request ->
            if (request.url.encodedPath == LOGIN_PATH) {
                // This is the login request; execute it directly.
                execute(request)
            } else {
                val currentToken = mutex.withLock { token }
                val call = currentToken?.let {
                    request.header(TOKEN_HEADER, it)
                    execute(request)
                }

                if (call == null || call.response.status == HttpStatusCode.Forbidden) {
                    val updatedToken = mutex.withLock {
                        // Check that no other caller has already renewed the token.
                        if (token == currentToken) {
                            token = fetchToken(client)
                        }

                        token
                    }

                    request.header(TOKEN_HEADER, updatedToken)
                    execute(request)
                } else {
                    call
                }
            }
        }
    }

    /**
     * Send a login request to the Vault API to obtain an access token for the configured role ID and secret ID. Use
     * [client] for this purpose.
     */
    private suspend fun fetchToken(client: HttpClient): String {
        logger.info("Requesting new Vault token.")
        val loginResponse: VaultLoginResponse = client.post(LOGIN_PATH) {
            setBody(config.credentials)
        }.body()

        return loginResponse.auth.clientToken
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
