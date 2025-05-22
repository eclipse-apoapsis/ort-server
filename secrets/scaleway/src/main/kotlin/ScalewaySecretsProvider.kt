/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.secrets.scaleway

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
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

import java.net.URLEncoder

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.Secret
import org.eclipse.apoapsis.ortserver.secrets.SecretsProvider
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking

import org.slf4j.LoggerFactory

private const val PRODUCT_ENDPOINT = "secret-manager"
private const val LATEST_REVISION = "latest"

/**
 * This is an experimental secrets provider implementation for Scaleway's
 * [Secret Manager](https://www.scaleway.com/en/developers/api/secret-manager/).
 *
 * In contrast to other Scaleway product endpoints, the Secret Manager API is still in beta. It may suffer from bugs or
 * change responses.
 */
class ScalewaySecretsProvider(
    private val config: ScalewayConfiguration
) : SecretsProvider {
    companion object {
        private val logger = LoggerFactory.getLogger(ScalewaySecretsProvider::class.java)
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            defaultRequest {
                // For the general endpoint pattern see https://www.scaleway.com/en/developers/api/#endpoints.
                val serverUrl = config.serverUrl.removeSuffix("/")
                url("$serverUrl/$PRODUCT_ENDPOINT/${config.apiVersion}/regions/${config.region}/")

                header("X-Auth-Token", config.secretKey)
                contentType(ContentType.Application.Json)
            }

            install(ContentNegotiation) {
                json(json)
            }

            expectSuccess = true
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun readSecret(path: Path): Secret? = runBlocking {
        // See https://www.scaleway.com/en/developers/api/secret-manager/#path-secret-versions-access-a-secrets-version-using-the-secrets-name-and-path.
        val response = client.get("secrets-by-path/versions/$LATEST_REVISION/access") {
            parameter("project_id", config.projectId)

            val (secretPath, secretName) = path.toScaleway()
            parameter("secret_path", secretPath)
            parameter("secret_name", secretName)

            expectSuccess = false
        }

        when {
            // It is a confirmed bug that the current API returns "InternalServerError" when trying to read a
            // non-existing secret.
            response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.InternalServerError -> {
                logger.debug("No secret to read at $path.")

                null
            }

            response.status.isSuccess() -> {
                val secretResponse = response.body<SecretsAccessResponse>()

                logger.debug("Read a secret at $path.")

                Secret(String(Base64.decode(secretResponse.data)))
            }

            else -> throw ClientRequestException(response, response.body())
        }
    }

    override fun writeSecret(path: Path, secret: Secret) = runBlocking {
        val listResponse = listSecrets(path)

        val secretId = if (listResponse.totalCount < 1) {
            val createResponse = createSecret(path)
            createResponse.id.also { id ->
                logger.debug("Created a secret at $path as it did not exist before.")
            }
        } else {
            check(listResponse.totalCount == 1)

            listResponse.secrets.first().id.also { id ->
                logger.debug("Secret at $path already exists and is not created again.")
            }
        }

        val createResponse = createVersion(secretId, secret.value)

        logger.debug("Created version ${createResponse.revision} for secret at $path.")

        check(createResponse.latest)
    }

    private suspend fun listSecrets(path: Path): SecretsListResponse {
        // See https://www.scaleway.com/en/developers/api/secret-manager/#path-secrets-list-secrets.
        val response = client.get("secrets") {
            parameter("project_id", config.projectId)

            val (secretPath, secretName) = path.toScaleway()
            parameter("path", secretPath)
            parameter("name", secretName)
        }

        return response.body<SecretsListResponse>()
    }

    private suspend fun createSecret(path: Path): ScalewaySecret {
        // See https://www.scaleway.com/en/developers/api/secret-manager/#path-secrets-create-a-secret.
        val response = client.post("secrets") {
            val (secretPath, secretName) = path.toScaleway()

            setBody(
                SecretCreateRequest(
                    projectId = config.projectId,
                    path = secretPath,
                    name = secretName
                )
            )
        }

        return response.body<ScalewaySecret>()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun createVersion(secretId: String, value: String): VersionCreateResponse {
        // See https://www.scaleway.com/en/developers/api/secret-manager/#path-secret-versions-create-a-version.
        val response = client.post("secrets/$secretId/versions") {
            setBody(
                VersionCreateRequest(Base64.encode(value.toByteArray()))
            )
        }

        return response.body<VersionCreateResponse>()
    }

    override fun removeSecret(path: Path) = runBlocking {
        val listResponse = listSecrets(path)

        if (listResponse.totalCount < 1) {
            logger.debug("Skipping deletion of secret at $path as it does not exist.")
            return@runBlocking
        }

        check(listResponse.totalCount == 1)

        val secretId = listResponse.secrets.first().id

        // See https://www.scaleway.com/en/developers/api/secret-manager/#path-secrets-delete-a-secret.
        val response = client.delete("secrets/$secretId")

        if (response.status != HttpStatusCode.NoContent) throw ClientRequestException(response, response.body())

        logger.debug("Deleted the secret at $path.")
    }

    override fun createPath(id: HierarchyId, secretName: String): Path {
        val secretType = when (id) {
            is OrganizationId -> "organization_${id.value}"
            is ProductId -> "product_${id.value}"
            is RepositoryId -> "repository_${id.value}"
        }

        // There should be no characters that need encoding. Actually, Scaleway is even stricter to require its path and
        // name to match "^[_a-zA-Z0-9]([-_.a-zA-Z0-9]*[_a-zA-Z0-9])?$" currently, but as that might change any time, do
        // not reimplement the strict check here, but only this more lenient check that is required to be able to choose
        // a reserved character as the type / name separator.
        check(URLEncoder.encode(secretType, Charsets.UTF_8) == secretType) {
            "The secret type must not contain characters that require URL encoding."
        }

        check(URLEncoder.encode(secretName, Charsets.UTF_8) == secretName) {
            "The secret name must not contain characters that require URL encoding."
        }

        // The returned path needs to be absolute.
        return Path("/$secretType/$secretName")
    }
}

internal fun Path.toScaleway(): Pair<String, String> =
    path.substringBeforeLast('/') to path.substringAfterLast('/')
