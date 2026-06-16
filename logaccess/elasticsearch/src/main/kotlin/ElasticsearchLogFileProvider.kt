/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.logaccess.elasticsearch

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

import java.io.File

import kotlin.time.Instant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

import org.eclipse.apoapsis.ortserver.logaccess.LogFileProvider
import org.eclipse.apoapsis.ortserver.model.LogLevel
import org.eclipse.apoapsis.ortserver.model.LogSource
import org.eclipse.apoapsis.ortserver.shared.ktorclientutils.createHttpClient

import org.slf4j.LoggerFactory

/**
 * An implementation of the [LogFileProvider] interface that retrieves log data from Elasticsearch.
 *
 * The provider queries a configured index or index pattern for a single ORT run and component, then writes the
 * returned log messages to a local file in chronological order.
 */
class ElasticsearchLogFileProvider(
    /** The configuration for this provider. */
    private val config: ElasticsearchConfig
) : LogFileProvider {
    companion object {
        private val logger = LoggerFactory.getLogger(ElasticsearchLogFileProvider::class.java)
    }

    /** The HTTP client for sending requests to the Elasticsearch instance. */
    private val elasticsearchClient = createClient()

    // The Kubernetes namespace is not coming from the log lines, therefore the field prefix is not applied.
    private val namespaceField = config.namespaceField

    // Apply the configured field prefix to all fields taken from the log lines.
    private val componentIndexField = prefix("mdc.component.keyword")
    private val levelField = prefix("level")
    private val levelIndexField = prefix("level.keyword")
    private val messageField = prefix("formattedMessage")
    private val ortRunIdIndexField = prefix("mdc.ortRunId.keyword")
    private val sequenceNumberField = prefix("sequenceNumber")
    private val throwableField = prefix("throwable")
    private val timestampField = prefix("timestamp")

    override suspend fun downloadLogFile(
        ortRunId: Long,
        source: LogSource,
        levels: Set<LogLevel>,
        startTime: Instant,
        endTime: Instant,
        directory: File,
        fileName: String
    ): File {
        val logFile = directory.resolve(fileName)
        logFile.bufferedWriter().use { out ->
            var searchAfter: List<JsonElement>? = null

            do {
                val response = queryPage(ortRunId, source, levels, startTime, endTime, searchAfter)
                val hits = response.hits.hits

                hits.forEach { hit ->
                    val statement = hit.source.stringAtPath(messageField)
                    if (statement == null) {
                        logger.warn("Skipping Elasticsearch hit '{}' because no message field is present.", hit.id)
                    } else {
                        hit.source.stringAtPath(timestampField)?.toLongOrNull()?.let { timestamp ->
                            out.write("${Instant.fromEpochMilliseconds(timestamp)} ")
                        }
                        hit.source.stringAtPath(levelField)?.let { out.write("$it ") }
                        out.write(statement)
                        hit.source.stringAtPath(throwableField)?.let { out.write("\n$it") }
                        out.newLine()
                    }
                }

                searchAfter = if (hits.size >= config.pageSize) {
                    hits.last().sort.takeIf(List<JsonElement>::isNotEmpty)
                        ?: error("Elasticsearch response is missing sort values for pagination.")
                } else {
                    null
                }
            } while (searchAfter != null)
        }

        return logFile
    }

    private suspend fun queryPage(
        ortRunId: Long,
        source: LogSource,
        levels: Set<LogLevel>,
        startTime: Instant,
        endTime: Instant,
        searchAfter: List<JsonElement>?
    ): ElasticsearchResponse {
        val requestBody = createSearchRequest(ortRunId, source, levels, startTime, endTime, searchAfter)
        logger.debug("Sending Elasticsearch log query: {}.", requestBody)

        return elasticsearchClient.post("${config.serverUrl.trimEnd('/')}/${config.index}/_search") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body()
    }

    private fun createSearchRequest(
        ortRunId: Long,
        source: LogSource,
        levels: Set<LogLevel>,
        startTime: Instant,
        endTime: Instant,
        searchAfter: List<JsonElement>?
    ): JsonObject =
        buildJsonObject {
            put("size", config.pageSize)
            putJsonArray("_source") {
                add(JsonPrimitive(levelField))
                add(JsonPrimitive(messageField))
                add(JsonPrimitive(throwableField))
                add(JsonPrimitive(timestampField))
            }
            putJsonObject("query") {
                putJsonObject("bool") {
                    putJsonArray("filter") {
                        add(termQuery(namespaceField, JsonPrimitive(config.namespace)))
                        add(termQuery(componentIndexField, JsonPrimitive(source.component)))
                        add(termQuery(ortRunIdIndexField, JsonPrimitive(ortRunId.toString())))
                        add(termsQuery(levelIndexField, levels.map { it.name }))
                        add(rangeQuery(startTime, endTime))
                    }
                }
            }
            putJsonArray("sort") {
                add(sortBy(timestampField))
                add(sortBy(sequenceNumberField))
            }
            if (searchAfter != null) {
                put("search_after", JsonArray(searchAfter))
            }
        }

    /** Generate a term query for an exact match on [field]. */
    private fun termQuery(field: String, value: JsonPrimitive): JsonObject =
        buildJsonObject {
            putJsonObject("term") {
                put(field, value)
            }
        }

    /** Generate a terms query that matches any of the given [values] for [field]. */
    private fun termsQuery(field: String, values: List<String>): JsonObject =
        buildJsonObject {
            putJsonObject("terms") {
                putJsonArray(field) {
                    values.forEach { add(JsonPrimitive(it)) }
                }
            }
        }

    /** Generate a range query for the configured timestamp field. */
    private fun rangeQuery(startTime: Instant, endTime: Instant): JsonObject =
        buildJsonObject {
            putJsonObject("range") {
                putJsonObject(timestampField) {
                    put("gte", startTime.toEpochMilliseconds())
                    put("lte", endTime.toEpochMilliseconds())
                    put("format", "epoch_millis")
                }
            }
        }

    /** Generate a sort definition for [field]. */
    private fun sortBy(field: String, order: String? = "asc"): JsonObject =
        buildJsonObject {
            putJsonObject(field) {
                put("order", order)
            }
        }

    /**
     * Create an [HttpClient] with a configuration to communicate with Elasticsearch.
     */
    private fun createClient(): HttpClient =
        createHttpClient(ElasticsearchConfig.HTTP_CLIENT_OVERRIDES_PATH) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
            }

            if (config.apiKey != null) {
                defaultRequest { header(HttpHeaders.Authorization, "ApiKey ${config.apiKey}") }
            } else if (config.username != null && config.password != null) {
                install(Auth) {
                    basic {
                        credentials {
                            BasicAuthCredentials(config.username, config.password)
                        }
                        sendWithoutRequest { true }
                    }
                }
            }

            expectSuccess = true
        }

    private fun prefix(field: String) = if (config.fieldPrefix != null) "${config.fieldPrefix}.$field" else field
}

/**
 * Return the string value located at [path] within this object, where [path] is a dot-separated field name, or `null`,
 * if any segment is missing or the final sigment is not a [JsonPrimitive].
 */
internal fun JsonObject.stringAtPath(path: String): String? {
    var current: JsonElement = this
    path.split('.').forEach { segment ->
        current = (current as? JsonObject)?.get(segment) ?: return null
    }

    return (current as? JsonPrimitive)?.content
}
