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

package org.eclipse.apoapsis.ortserver.logaccess.loki

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json

import java.io.File

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.logaccess.LogFileProvider
import org.eclipse.apoapsis.ortserver.model.LogLevel
import org.eclipse.apoapsis.ortserver.model.LogSource

import org.slf4j.LoggerFactory

/**
 * An implementation of the [LogFileProvider] interface that interacts with a Grafana Loki server.
 *
 * This implementation assumes that Loki is configured to return only a single stream of log entries for a given
 * range query. Otherwise, it cannot be guaranteed that the log entries are order correctly, and no entries are
 * missed, since fetching is done over multiple chunks.
 *
 * See https://grafana.com/oss/loki/.
 */
class LokiLogFileProvider(
    /** The configuration for this provider. */
    private val config: LokiConfig
) : LogFileProvider {
    companion object {
        /** The header to define the tenant ID in multi-tenant mode. */
        private const val TENANT_HEADER = "X-Scope-OrgID"

        /** Default value for HTTP client timeout in seconds */
        private const val HTTP_CLIENT_DEFAULT_TIMEOUT_SEC = 30

        private val logger = LoggerFactory.getLogger(LokiLogFileProvider::class.java)

        /**
         * Extract the log statements from the given [response]. If the response contains multiple streams, the
         * statements need to be ordered manually. This typically indicates a wrong configuration of Loki, since
         * with multiple streams, a defined order of log entries over multiple chunks cannot be guaranteed.
         * Therefore, log a warning in this case.
         */
        private fun extractOrderedLogStatements(response: LokiResponse): List<LogStatement> {
            val statements = response.logStatements()

            return if (response.data.result.size > 1) {
                logger.warn(
                    "Received multiple streams in Loki response. Please check the configuration in Loki. " +
                            "The order of log entries may be incorrect over multiple chunks."
                )

                statements.sortedBy { it.timestamp }
            } else {
                statements
            }
        }
    }

    /** The HTTP client for sending requests to the Loki instance. */
    private val lokiClient = createClient()

    override suspend fun downloadLogFile(
        ortRunId: Long,
        source: LogSource,
        levels: Set<LogLevel>,
        startTime: Instant,
        endTime: Instant,
        directory: File,
        fileName: String
    ): File {
        val queryStr = constructQuery(ortRunId, source, levels)
        logger.info("Sending log data query to Loki:\n{}", queryStr)

        val logFile = directory.resolve(fileName)
        logFile.bufferedWriter().use { out ->
            tailrec suspend fun downloadChunk(from: String, lastChunk: List<LogStatement>) {
                logger.debug(
                    "Querying chunk of log data for run '{}' and source '{}' starting at '{}'.",
                    ortRunId,
                    source,
                    from
                )

                val httpResponse = lokiClient.get {
                    parameter("start", from)
                    parameter("end", endTime.epochSeconds + 1)
                    parameter("limit", config.limit)
                    parameter("direction", "forward")
                    parameter("query", queryStr)
                    url("/loki/api/v1/query_range")
                }

                val statements = extractOrderedLogStatements(httpResponse.body<LokiResponse>())
                val deDuplicatedStatements = statements.takeIf { lastChunk.isEmpty() }
                    ?: deDuplicateStatements(statements, lastChunk)

                deDuplicatedStatements.forEach { statement ->
                    out.write(statement.statement)
                    out.newLine()
                }

                if (statements.size >= config.limit && deDuplicatedStatements.isNotEmpty()) {
                    val lastTimestamp = statements.last().timestamp

                    if (lastTimestamp != from) {
                        downloadChunk(lastTimestamp, deDuplicatedStatements)
                    } else {
                        logger.error(
                            "Possible loss of log data for run '{}' and source '{}' " +
                            "due to number of identical timestamps exceeds chunk size limit.",
                            ortRunId,
                            source
                        )
                    }
                }
            }

            downloadChunk(startTime.epochSeconds.toString(), emptyList())
        }

        return logFile
    }

    /**
     * Generate a Loki query string to query log information for the given [ortRunId], [source], and [levels].
     */
    private fun constructQuery(
        ortRunId: Long,
        source: LogSource,
        levels: Set<LogLevel>
    ): String {
        val levelCriterion = levels.joinToString(separator = "|", prefix = "(", postfix = ")") { it.name }
        return """{namespace="${config.namespace}",component="${source.component}"}""" +
                """ |~ "level=$levelCriterion" |~ "ortRunId=$ortRunId""""
    }

    /**
     * Create an [HttpClient] with a configuration to communicate with the Loki service.
     */
    private fun createClient(): HttpClient =
        HttpClient(OkHttp) {
            defaultRequest {
                url(config.serverUrl)
                config.tenantId?.let { tenant ->
                    header(TENANT_HEADER, tenant)
                }
            }

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
            }

            if (config.username != null && config.password != null) {
                install(Auth) {
                    basic {
                        credentials {
                            BasicAuthCredentials(config.username, config.password)
                        }
                        sendWithoutRequest { true }
                    }
                }
            }

            install(HttpTimeout) {
                val httpClientTimeoutMillis = (config.timeoutSec ?: HTTP_CLIENT_DEFAULT_TIMEOUT_SEC) * 1000L
                requestTimeoutMillis = httpClientTimeoutMillis
                connectTimeoutMillis = httpClientTimeoutMillis
                socketTimeoutMillis = httpClientTimeoutMillis
            }

            expectSuccess = true
        }
}

/**
 * Remove items from the given [statements] that are already contained in [lastChunk]. Since the next chunk is
 * queried with a start time taken from the last item of the previous chunk, there can be overlapping statements
 * that need to be removed.
 */
private fun deDuplicateStatements(statements: List<LogStatement>, lastChunk: List<LogStatement>): List<LogStatement> {
    val lastWritten = lastChunk.last()
    return statements.dropWhile { it.timestamp < lastWritten.timestamp || it == lastWritten }
}
