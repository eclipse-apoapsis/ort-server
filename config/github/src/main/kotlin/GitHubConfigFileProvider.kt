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
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel

import java.io.File
import java.io.InputStream

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking

import org.slf4j.LoggerFactory

class GitHubConfigFileProvider(
    /** The HTTP client to be used for all requests. */
    private val httpClient: HttpClient,

    /**
     * The base URL for accessing the GitHub REST API for the repository that contains the managed configuration files.
     */
    private val baseUrl: String,

    /** The default branch to be used if no context is provided. */
    private val defaultBranch: String,

    /** The cache for storing already fetched configuration data. */
    private val cache: GitHubConfigCache
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
         * Configuration property that defines the default branch.
         */
        const val DEFAULT_BRANCH = "gitHubDefaultBranch"

        /**
         * The path to the secret containing of the GitHub API token of the user with access rights for the given
         * repository.
         */
        val TOKEN = Path("gitHubApiToken")

        /**
         * Configuration property that defines the root path of a file-based cache. If this is defined, the provider
         * uses a [GitHubConfigFileCache] object to cache the fetched configuration files. Otherwise, caching is
         * disabled.
         */
        const val CACHE_DIRECTORY = "gitHubCacheDirectory"

        /**
         * Configuration property that defines the interval for obtaining read locks (in seconds) when using the
         * file-based configuration cache. This is only evaluated if a cache directory is defined.
         */
        const val LOCK_CHECK_INTERVAL_SEC = "gitHubCacheLockCheckIntervalSec"

        /**
         * Configuration property that determines the maximum age of revisions stored in the cache (in days). Older
         * revisions are cleaned up regularly.
         */
        const val CACHE_MAX_AGE_DAYS = "gitHubCacheMaxAgeDays"

        /**
         * Configuration property that determines how often a cleanup operation is performed on the cache. The integer
         * value of this property is roughly the number of ORT runs after which a cleanup is done. Since no state can
         * be stored over multiple ORT runs, this frequency is not enforced, but an approach based on probability is
         * taken.
         */
        const val CACHE_CLEANUP_RATIO = "gitHubCacheCleanupRatio"

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

        /**
         * The header used by the GitHub REST API to return the number of remaining requests before the rate limit in
         * the current time range is exceeded.
         */
        private const val HEADER_RATE_LIMIT_REMAINING = "x-ratelimit-remaining"

        /**
         * The header used by the GitHub REST API that defines when the rate limit will be reset.
         * See https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api?apiVersion=2022-11-28.
         */
        private const val HEADER_RATE_LIMIT_RESET = "x-ratelimit-reset"

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
            return GitHubConfigFileProvider(
                createClient(secretProvider),
                baseUrl,
                defaultBranch,
                createCache(config)
            )
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

        /**
         * Create the cache for configuration data based on the given [config].
         */
        private fun createCache(config: Config): GitHubConfigCache =
            config.getStringOrNull(CACHE_DIRECTORY)?.let { cacheDir ->
                logger.debug("Using file-based cache in directory '{}'.", cacheDir)

                val lockCheckInterval = config.getInt(LOCK_CHECK_INTERVAL_SEC)
                val maxAge = config.getInt(CACHE_MAX_AGE_DAYS)
                val cleanUpRatio = config.getInt(CACHE_CLEANUP_RATIO)
                GitHubConfigFileCache(File(cacheDir), lockCheckInterval.seconds, cleanUpRatio, maxAge.days)
            } ?: GitHubConfigNoCache()

        /**
         * Check whether the given [response] indicates a request that failed because the current rate limit is
         * exceeded. If so, return the time in seconds until the rate limit will be reset. Otherwise, return *null*.
         */
        private fun checkForRateLimitRetry(response: HttpResponse): Long? {
            if (response.status != HttpStatusCode.Forbidden) return null

            val remaining = response.headers[HEADER_RATE_LIMIT_REMAINING]?.toIntOrNull()
            val reset = response.headers[HEADER_RATE_LIMIT_RESET]?.toLongOrNull()

            return reset.takeIf { remaining != null && remaining <= 0 }
        }
    }

    override fun resolveContext(context: Context): Context {
        val branchName = if (context == ConfigManager.EMPTY_CONTEXT) defaultBranch else context.name
        val response = sendHttpRequest("/branches/$branchName")

        if (!response.isPresent()) {
            throw ConfigException("The branch '${context.name}' is not found in the config repository.", null)
        }

        val jsonBody = getJsonBody(response)

        val commitId = jsonBody.jsonObject["commit"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
            ?: throw NoSuchFieldException("Couldn't find SHA-1 commit ID for the branch ${context.name}")

        if (branchName == defaultBranch) {
            cache.cleanup(commitId)
        }

        return Context(commitId)
    }

    /**
     * This function can be used to download the raw file content from a GitHub repository. If the provided path
     * refers a directory, GitHub API will return a JSON array with the directory content. In this case, as well as in
     * the case when the returned 'Content Type' header is neither of raw file or json, or it is missing, a
     * [ConfigException] is thrown with the description of the cause.
     */
    override fun getFile(context: Context, path: Path): InputStream = runBlocking {
        cache.getOrPutFile(context.name, path.path) { downloadFile(context, path) }
    }

    override fun contains(context: Context, path: Path): Boolean {
        val response = sendHttpRequest(
            "/contents/${path.path}?ref=${context.name}",
            checkSuccess = false
        )

        if (!response.isPresent()) return false

        val jsonBody = getJsonBody(response)

        return !jsonBody.isDirectory() && jsonBody.isFile()
    }

    override fun listFiles(context: Context, path: Path): Set<Path> = runBlocking {
        cache.getOrPutFolderContent(context.name, path.path) { downloadFolderContent(context, path) }
            .map { Path(it) }
            .toSet()
    }

    /**
     * Send a request to the GitHub REST API as defined by [baseUrl] with the provided [path]. If the [checkSuccess]
     * flag is *true*, also check if the response is successful and throw a [ConfigException] if not.
     */
    private fun sendHttpRequest(
        path: String,
        contentType: String = JSON_CONTENT_TYPE_HEADER,
        checkSuccess: Boolean = true
    ): HttpResponse = runBlocking {
        val response = sendHttpRequestWithRetry(path, contentType)

        if (checkSuccess && !response.status.isSuccess()) {
            logger.error("Error response from GitHub API request: ${response.status}.")
            logger.info("Response body: ${response.body<String>()}")

            throw ConfigException("Error response from GitHub API request: ${response.status}.", null)
        } else {
            response
        }
    }

    /**
     * Send a request to the GitHub REST API as defined by [baseUrl] with the provided [path] with a retry mechanism in
     * case the request fails due to exceeded rate limits.
     */
    private tailrec suspend fun sendHttpRequestWithRetry(path: String, contentType: String): HttpResponse {
        val requestUrl = "$baseUrl$path"
        logger.debug("GET '{}'", requestUrl)

        val response = httpClient.get(requestUrl) {
            header("Accept", contentType)
        }

        val rateLimitReset = checkForRateLimitRetry(response)
        return if (rateLimitReset != null) {
            val resetAt = Instant.fromEpochSeconds(rateLimitReset)
            val delay = resetAt - Clock.System.now()
            logger.warn("Rate limit exceeded. Retrying in {} seconds.", delay)

            delay(delay)
            sendHttpRequestWithRetry(path, contentType)
        } else {
            response
        }
    }

    /**
     * Download the file for the given [context] and [path] and return a channel to its content. Throw a
     * [ConfigException] if download fails.
     */
    private suspend fun downloadFile(context: Context, path: Path): ByteReadChannel {
        val response = sendHttpRequest(
            "/contents/${path.path}?ref=${context.name}",
            RAW_CONTENT_TYPE_HEADER
        )

        response.headers["Content-Type"]?.let {
            if (it.contains(RAW_CONTENT_TYPE_HEADER)) {
                return response.bodyAsChannel()
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

    /**
     * Query the GitHub REST API for the content of the folder at the given [path] at the revision specified by
     * [context]. Throw a [ConfigException] if the path does not exist or is not a directory.
     */
    private fun downloadFolderContent(context: Context, path: Path): Set<String> {
        val response = sendHttpRequest("/contents/${path.path}?ref=${context.name}")

        val jsonBody = getJsonBody(response)

        if (!jsonBody.isDirectory()) {
            throw ConfigException("The provided path `${path.path}` does not refer a directory.", null)
        }

        return jsonBody.jsonArray
            .filter { it.isFile() }
            .mapNotNull { it.jsonObject["path"]?.jsonPrimitive?.content }
            .toSet()
    }
}

private fun getJsonBody(response: HttpResponse): JsonElement {
    if (!response.isPresent()) {
        throw ConfigException("The requested path doesn't exist in the specified branch.", null)
    }

    return runBlocking { Json.parseToJsonElement(response.body<String>()) }
}

private fun JsonElement.isFile() = this.jsonObject["type"]?.jsonPrimitive?.content == "file"

private fun JsonElement.isDirectory() =
    this is JsonArray || this.jsonObject["type"]?.jsonPrimitive?.content == "dir"

private fun HttpResponse.isPresent() = status != HttpStatusCode.NotFound
