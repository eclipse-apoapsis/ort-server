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

package org.eclipse.apoapsis.ortserver.config.github

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

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigFileProvider
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProvider
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrDefault

import org.slf4j.LoggerFactory

class GitHubConfigFileProvider(
    /** The HTTP client to be used for all requests. */
    private val httpClient: HttpClient,

    /**
     * The base URL for accessing the GitHub REST API for the repository that contains the managed configuration files.
     */
    private val baseUrl: String,

    /** The default branch to be used if the default context is provided. */
    private val defaultBranch: String
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
         * Configuration property that defines the default branch to be used if the user provides the default context.
         */
        const val DEFAULT_BRANCH = "gitHubDefaultBranch"

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

        /** The default URL to the GitHub REST API. */
        private const val DEFAULT_GITHUB_API_URL = "https://api.github.com"

        /** The default value for the default branch property. */
        private const val DEFAULT_REPOSITORY_BRANCH = "main"

        private val logger = LoggerFactory.getLogger(GitHubConfigFileProvider::class.java)

        /**
         * Create a new instance of [GitHubConfigFileProvider] that is initialized based on the given [config] and
         * [secretProvider].
         */
        fun create(config: Config, secretProvider: ConfigSecretProvider): GitHubConfigFileProvider {
            val owner = config.getString(REPOSITORY_OWNER)
            val repository = config.getString(REPOSITORY_NAME)
            val gitHubApiUrl = config.getStringOrDefault(GITHUB_API_URL, DEFAULT_GITHUB_API_URL)
            val defaultBranch = config.getStringOrDefault(DEFAULT_BRANCH, DEFAULT_REPOSITORY_BRANCH)

            logger.info("Creating GitHubConfigFileProvider.")
            logger.debug("GitHub URI: '{}'.", gitHubApiUrl)
            logger.debug("GitHub repository: '{}'.", repository)
            logger.debug("GitHub repository owner: '{}'.", owner)
            logger.debug("GitHub default branch: '{}'.", defaultBranch)

            val baseUrl = "$gitHubApiUrl/repos/$owner/$repository"
            return GitHubConfigFileProvider(createClient(secretProvider), baseUrl, defaultBranch)
        }

        /**
         * Create the HTTP client to be used for all requests against the GitHub REST API.
         */
        private fun createClient(secretProvider: ConfigSecretProvider): HttpClient {
            return HttpClient(OkHttp) {
                defaultRequest {
                    header("Authorization", "Bearer ${secretProvider.getSecret(TOKEN)}")
                }
            }
        }
    }

    override fun resolveContext(context: Context): Context {
        val branchName = if (context == ConfigManager.DEFAULT_CONTEXT) defaultBranch else context.name
        val response = sendHttpRequest("/branches/$branchName", JSON_CONTENT_TYPE_HEADER)

        if (!response.isPresent()) {
            throw ConfigException("The branch '${context.name}' is not found in the config repository.", null)
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
            "/contents/${path.path}?ref=${context.name}",
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
            "/contents/${path.path}?ref=${context.name}",
            JSON_CONTENT_TYPE_HEADER
        )

        if (!response.isPresent()) return false

        val jsonBody = getJsonBody(response)

        return !jsonBody.isDirectory() && jsonBody.isFile()
    }

    override fun listFiles(context: Context, path: Path): Set<Path> {
        val response = sendHttpRequest(
            "/contents/${path.path}?ref=${context.name}",
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

    /**
     * Send a request to the GitHub REST API as defined by [baseUrl] with the provided [path].
     */
    private fun sendHttpRequest(
        path: String,
        contentType: String
    ) = runBlocking {
        val requestUrl = "$baseUrl$path"
        logger.debug("GET '{}'", requestUrl)

        httpClient.get(requestUrl) {
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
