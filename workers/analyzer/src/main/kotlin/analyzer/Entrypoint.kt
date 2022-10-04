/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.analyzer

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json

import java.io.File

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.analyzer.managers.Npm
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.server.api.v1.AnalyzerJob

fun main() {
    // This is the entry point of the Analyzer Docker image. It calls the Analyzer from ORT programmatically by
    // interfacing on its APIs.
    println("Hello World")

    // This tests that ORT's classes can be accessed as well as the CLI tools of the Docker image.
    val npm = Npm.Factory().create(File("."), AnalyzerConfiguration(), RepositoryConfiguration())
    val version = npm.getVersion()
    println("Npm version is $version.")

    // Reading environment variables, which could be set e.g. in a docker compose file. Otherwise, use default
    // values. This is only an experimental approach to get access to ORT server specific environment variables,
    // which could be improved by using a configuration file.
    val host = System.getenv("ORT_SERVER_URL") ?: "http://localhost:8080"
    val user = System.getenv("ORT_SERVER_USER") ?: "admin"
    val password = System.getenv("ORT_SERVER_PASSWORD") ?: "admin"
    val authUrl = System.getenv("ORT_SERVER_AUTH_URL")
        ?: "http://localhost:8081/realms/master/protocol/openid-connect/token"
    val clientId = System.getenv("ORT_SERVER_CLIENT_ID") ?: "ort-server"
    println("ORT server base URL: $host")
    println("ORT server user: $user")
    println("ORT server password. $password")
    println("ORT server authentication URL: $authUrl")
    println("ORT server client ID: $clientId")

    runBlocking {
        val client = ServerClient.create(host, user, password, clientId, authUrl)

        while (true) {
            delay(10 * 1000)

            client.getScheduledAnalyzerJob()?.let { startedJob ->
                println("Analyzer job with id '${startedJob.id}' started at ${startedJob.startedAt}.")
                println("Running...")
                delay(10 * 1000)
                client.finishAnalyzerJob(startedJob.id)?.let { finishedJob ->
                    println("Analyzer job with id '${finishedJob.id} finished at ${finishedJob.finishedAt}")
                }
            }
        }
    }
}

/**
 * A simple ORT server client.
 */
class ServerClient(
    private val url: String,
    private val httpClient: HttpClient
) {
    companion object {
        fun create(url: String, username: String, password: String, clientId: String, authUrl: String): ServerClient {
            val client = HttpClient(OkHttp) {
                expectSuccess = true

                defaultRequest {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                }

                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                        }
                    )
                }

                install(Auth) {
                    bearer {
                        refreshTokens {
                            val tokenInfo: TokenInfo = runCatching {
                                client.submitForm(
                                    url = authUrl,
                                    formParameters = Parameters.build {
                                        append("client_id", clientId)
                                        append("grant_type", "refresh_token")
                                        append("refresh_token", oldTokens?.refreshToken ?: "")
                                    }
                                )
                            }.getOrElse {
                                client.submitForm(
                                    url = authUrl,
                                    formParameters = Parameters.build {
                                        append("client_id", clientId)
                                        append("grant_type", "password")
                                        append("username", username)
                                        append("password", password)
                                    }
                                )
                            }.body()

                            BearerTokens(tokenInfo.accessToken, tokenInfo.refreshToken)
                        }
                    }
                }
            }

            return ServerClient(url, client)
        }
    }

    suspend fun getScheduledAnalyzerJob(): AnalyzerJob? =
        runCatching {
            httpClient.post("$url/api/v1/jobs/analyzer/start")
        }.getOrNull()?.body()

    suspend fun finishAnalyzerJob(jobId: Long): AnalyzerJob? =
        runCatching {
            httpClient.post("$url/api/v1/jobs/analyzer/$jobId/finish")
        }.getOrNull()?.body()
}

/**
 * A data class representing information about the tokens received from the API client.
 */
@Serializable
private data class TokenInfo(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String
)
