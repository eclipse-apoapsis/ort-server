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

package org.ossreviewtoolkit.server.config.github

import com.typesafe.config.Config

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

import java.io.ByteArrayInputStream
import java.io.InputStream

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import org.ossreviewtoolkit.server.config.ConfigException
import org.ossreviewtoolkit.server.config.ConfigFileProvider
import org.ossreviewtoolkit.server.config.ConfigSecretProvider
import org.ossreviewtoolkit.server.config.Context
import org.ossreviewtoolkit.server.config.Path

class GitHubConfigFileProvider(
    private val config: Config,
    private val secretProvider: ConfigSecretProvider
) : ConfigFileProvider {
    companion object {
        /**
         * Configuration property for the base URL to access the GitHub API. Defaults to the standard GitHub API URL.
         */
        const val GITHUB_API_URL = "gitHubApiUrl"

        /**
         * The account owner of the repository. The name is not case-sensitive.
         */
        const val REPOSITORY_OWNER = "gitHubRepositoryOwner"

        /**
         * The name of the repository without the .git extension. The name is not case-sensitive.
         */
        const val REPOSITORY_NAME = "gitHubRepositoryName"

        /**
         * The path to the secret containing of the GitHub API token of the user with access rights for the given
         * repository.
         */
        val TOKEN = Path("gitHubApiToken")

        /**
         * The header value required to get the raw content of a file directly.
         */
        const val RAW_CONTENT_TYPE_HEADER = "application/vnd.github.raw"

        /**
         * The header value indicating that the response format is JSON.
         */
        const val JSON_CONTENT_TYPE_HEADER = "application/json"
    }

    private val owner = config.getString(REPOSITORY_OWNER)
    private val repository = config.getString(REPOSITORY_NAME)
    private val gitHubApiUrl = config.getString(GITHUB_API_URL)
    private val httpClient = createClient()

    override fun resolveContext(context: Context): Context {
        val response =
            sendHttpRequest("$gitHubApiUrl/repos/$owner/$repository/branches/${context.name}", JSON_CONTENT_TYPE_HEADER)

        if (!response.isPresent()) {
            throw ConfigException("The branch ${context.name} is not found in the repository $repository", null)
        }

        val jsonBody = getJsonBody(response)

        val commitId = jsonBody.jsonObject["commit"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
            ?: throw NoSuchFieldException("Couldn't find SHA-1 commit ID for the branch ${context.name}")

        return Context(commitId)
    }

    /**
     * This function can be used to download the raw file content from a GitHub repository. If the provided path
     * refers a directory, GitHub API will return a JSON array with the directory content. In this case, as well as in
     * the case when the returned 'Content Type' header is neither of raw file or json, or it is missing, a
     * [ConfigException] is thrown with the description of the cause.
     */
    override fun getFile(context: Context, path: Path): InputStream {
        val response = sendHttpRequest(
            "$gitHubApiUrl/repos/$owner/$repository/contents/${path.path}?ref=${context.name}",
            RAW_CONTENT_TYPE_HEADER
        )

        response.headers["Content-Type"]?.let {
            if (it.contains(RAW_CONTENT_TYPE_HEADER)) {
                return runBlocking { ByteArrayInputStream(response.body<ByteArray>()) }
            } else if (it.contains(JSON_CONTENT_TYPE_HEADER) && getJsonBody(response).isDirectory()) {
                throw ConfigException(
                    "The provided path `${path.path}` refers a directory rather than a file. An exact " +
                            "configuration file path should be provided.",
                    null
                )
            } else {
                throw ConfigException(
                    "The GitHub response has unsupported content type: '$it'",
                    null
                )
            }
        } ?: throw ConfigException("Invalid GitHub response received: the 'Content-Type' is missing.", null)
    }

    override fun contains(context: Context, path: Path): Boolean {
        val response = sendHttpRequest(
            "$gitHubApiUrl/repos/$owner/$repository/contents/${path.path}?ref=${context.name}",
            JSON_CONTENT_TYPE_HEADER
        )

        if (!response.isPresent()) return false

        val jsonBody = getJsonBody(response)

        return !jsonBody.isDirectory() && jsonBody.isFile()
    }

    override fun listFiles(context: Context, path: Path): Set<Path> {
        val response = sendHttpRequest(
            "$gitHubApiUrl/repos/$owner/$repository/contents/${path.path}?ref=${context.name}",
            JSON_CONTENT_TYPE_HEADER
        )

        val jsonBody = getJsonBody(response)

        if (!jsonBody.isDirectory()) {
            throw ConfigException("The provided path `${path.path}` does not refer a directory.", null)
        }

        return jsonBody.jsonArray
            .filter { it.isFile() }
            .mapNotNull { it.jsonObject["path"]?.jsonPrimitive?.content }
            .map { Path(it) }
            .toSet()
    }

    private fun createClient(): HttpClient {
        return HttpClient(OkHttp) {
            defaultRequest {
                url(config.getString(GITHUB_API_URL))
                header("Authorization", "Bearer ${secretProvider.getSecret(TOKEN)}")
            }

            // Required in order to handle "Not Found" responses manually.
            expectSuccess = false
        }
    }

    private fun sendHttpRequest(
        url: String,
        contentType: String
    ) = runBlocking {
        httpClient.get(url) {
            header("Accept", contentType)
        }
    }

    private fun getJsonBody(response: HttpResponse): JsonElement {
        validateResponseIsPresent(response)

        return runBlocking { Json.parseToJsonElement(response.body<String>()) }
    }

    private fun validateResponseIsPresent(response: HttpResponse) {
        if (!response.isPresent()) {
            throw ConfigException("The requested path doesn't exist in the specified branch.", null)
        }
    }

    private fun JsonElement.isFile() = this.jsonObject["type"]?.jsonPrimitive?.content == "file"

    private fun JsonElement.isDirectory() =
        this is JsonArray || this.jsonObject["type"]?.jsonPrimitive?.content == "dir"

    private fun HttpResponse.isPresent() = status != HttpStatusCode.NotFound
}
