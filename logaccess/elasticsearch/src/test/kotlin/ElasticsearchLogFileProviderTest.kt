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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.BasicCredentials
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode

import io.mockk.mockk

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.EnumSet
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.logaccess.LogFileService
import org.eclipse.apoapsis.ortserver.model.LogLevel
import org.eclipse.apoapsis.ortserver.model.LogSource

class ElasticsearchLogFileProviderTest : StringSpec() {
    private val server = WireMockServer(WireMockConfiguration.options().dynamicPort())

    override suspend fun beforeSpec(spec: Spec) {
        server.start()
    }

    override suspend fun afterSpec(spec: Spec) {
        server.stop()
    }

    override suspend fun beforeAny(testCase: TestCase) {
        server.resetAll()
    }

    private suspend fun sendTestRequest(source: LogSource, levels: Set<LogLevel>): File =
        ElasticsearchLogFileProvider(server.elasticsearchConfig()).testRequest(source, levels)

    init {
        "A correct log file should be returned" {
            val hits = generateHits(32)
            server.stubSearchRequest(hits)

            val logFile = sendTestRequest(LogSource.ADVISOR, EnumSet.of(LogLevel.ERROR))

            hits.checkLogFile(logFile)
        }

        "Log data should be loaded in chunks using search_after" {
            val firstPage = generateHits(LIMIT)
            val secondPage = generateHits(10, offset = LIMIT.toLong())

            server.stubSearchRequest(firstPage, source = LogSource.EVALUATOR)
            server.stubSearchRequest(secondPage, searchAfter = firstPage.last().sort, source = LogSource.EVALUATOR)

            val logFile = sendTestRequest(LogSource.EVALUATOR, EnumSet.of(LogLevel.ERROR))

            (firstPage + secondPage).checkLogFile(logFile)
            server.verify(
                postRequestedFor(
                    urlPathEqualTo("/$INDEX/_search")
                )
                    .withRequestBody(
                        matchingJsonPath(
                            "$.search_after[0]",
                            equalTo(firstPage.last().sort[0].toString())
                        )
                    )
                    .withRequestBody(
                        matchingJsonPath(
                            "$.search_after[1]",
                            equalTo((firstPage.last().sort[1] as JsonPrimitive).content)
                        )
                    )
            )
        }

        "The log level should correctly be evaluated" {
            val hits = generateHits(8)
            server.stubSearchRequest(hits, source = LogSource.REPORTER, levels = setOf(LogLevel.INFO, LogLevel.WARN))

            val logFile = sendTestRequest(LogSource.REPORTER, EnumSet.of(LogLevel.INFO, LogLevel.WARN))

            hits.checkLogFile(logFile)
        }

        "Malformed hits without message should be skipped" {
            val hits = generateHits(4)
            server.stubSearchRequest(
                hits.take(2) +
                    ElasticsearchHit(
                        "missing-message",
                        ElasticsearchSource(null),
                        listOf(JsonPrimitive(99), JsonPrimitive("x"))
                    ) + hits.takeLast(2),
                source = LogSource.CONFIG
            )

            val logFile = sendTestRequest(LogSource.CONFIG, EnumSet.of(LogLevel.ERROR))

            hits.checkLogFile(logFile)
        }

        "Failed requests should be handled" {
            server.stubFor(
                post(anyUrl())
                    .willReturn(aResponse().withStatus(HttpStatusCode.InternalServerError.value))
            )

            val exception = shouldThrow<ServerResponseException> {
                sendTestRequest(LogSource.SCANNER, EnumSet.of(LogLevel.DEBUG))
            }

            exception.response.status shouldBe HttpStatusCode.InternalServerError
        }

        "Basic Auth should be supported" {
            val hits = generateHits(16)
            server.stubSearchRequest(hits)

            val username = "elasticUser"
            val password = "elasticPass"
            val config = server.elasticsearchConfig().copy(username = username, password = password)
            val provider = ElasticsearchLogFileProvider(config)

            val logFile = provider.testRequest(LogSource.ADVISOR, EnumSet.of(LogLevel.ERROR))

            hits.checkLogFile(logFile)
            server.verify(
                postRequestedFor(urlPathEqualTo("/$INDEX/_search"))
                    .withBasicAuth(BasicCredentials(username, password))
            )
        }

        "API key auth should take precedence over basic auth" {
            val hits = generateHits(16)
            server.stubSearchRequest(hits)

            val config = server.elasticsearchConfig().copy(
                username = "elasticUser",
                password = "elasticPass",
                apiKey = "secret-api-key"
            )
            val provider = ElasticsearchLogFileProvider(config)

            val logFile = provider.testRequest(LogSource.ADVISOR, EnumSet.of(LogLevel.ERROR))

            hits.checkLogFile(logFile)
            server.verify(
                postRequestedFor(urlPathEqualTo("/$INDEX/_search"))
                    .withHeader("Authorization", equalTo("ApiKey secret-api-key"))
            )
        }

        "The timeout setting is evaluated" {
            val response = generateSearchResponse(generateHits(16))
            server.stubFor(
                post(urlPathEqualTo("/$INDEX/_search"))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(response)
                            .withFixedDelay(2000)
                    )
            )

            val config = server.elasticsearchConfig().copy(timeoutSec = 1)
            val provider = ElasticsearchLogFileProvider(config)

            shouldThrow<IOException> {
                provider.testRequest(LogSource.ADVISOR, EnumSet.of(LogLevel.ERROR))
            }
        }

        "The integration with LogFileService should work" {
            val configHits = generateHits(16, source = LogSource.CONFIG)
            val advisorHits = generateHits(16, source = LogSource.ADVISOR)

            server.stubSearchRequest(configHits, source = LogSource.CONFIG)
            server.stubSearchRequest(advisorHits, source = LogSource.ADVISOR)

            val elasticsearchConfig = server.elasticsearchConfig()
            val providerConfigMap = mapOf(
                "name" to "elasticsearch",
                "elasticsearchServerUrl" to elasticsearchConfig.serverUrl,
                "elasticsearchIndex" to elasticsearchConfig.index,
                "elasticsearchNamespace" to elasticsearchConfig.namespace,
                "elasticsearchPageSize" to elasticsearchConfig.pageSize
            )
            val configMap = mapOf(LogFileService.LOG_FILE_SERVICE_SECTION to providerConfigMap)

            val configManager = ConfigManager(
                ConfigFactory.parseMap(configMap),
                { mockk() },
                { mockk() },
                allowSecretsFromConfig = false
            )

            val logFileService = LogFileService.create(configManager, tempdir().toPath())
            val logArchive = logFileService.createLogFilesArchive(
                RUN_ID,
                setOf(LogSource.CONFIG, LogSource.ADVISOR),
                LogLevel.ERROR,
                START_TIME,
                END_TIME
            )

            logArchive.exists() shouldBe true
            val archiveContentDir = tempdir().resolve("archive")
            logArchive.unpackTo(archiveContentDir)

            archiveContentDir.walk().maxDepth(1).filter { it.isFile }
                .mapTo(mutableListOf()) { it.name } shouldContainExactlyInAnyOrder listOf("config.log", "advisor.log")

            configHits.checkLogFile(archiveContentDir.resolve("config.log"))
            advisorHits.checkLogFile(archiveContentDir.resolve("advisor.log"))
        }

        "The request should contain the expected query filters" {
            val hits = generateHits(1, source = LogSource.ANALYZER)
            server.stubSearchRequest(hits, source = LogSource.ANALYZER, levels = setOf(LogLevel.WARN, LogLevel.ERROR))

            sendTestRequest(LogSource.ANALYZER, EnumSet.of(LogLevel.WARN, LogLevel.ERROR))

            server.verify(
                postRequestedFor(urlPathEqualTo("/$INDEX/_search")).withExpectedSearchBody(
                    source = LogSource.ANALYZER,
                    levels = listOf(LogLevel.WARN, LogLevel.ERROR)
                )
            )
        }

        "Pagination should terminate after an empty page" {
            val firstPage = generateHits(LIMIT)

            server.stubSearchRequest(firstPage)
            server.stubSearchRequest(emptyList(), searchAfter = firstPage.last().sort)

            val logFile = sendTestRequest(LogSource.ADVISOR, EnumSet.of(LogLevel.ERROR))

            firstPage.checkLogFile(logFile)
        }

        "A full page without sort values should fail" {
            val hits = generateHits(LIMIT)
            val malformedHits = hits.dropLast(1) + hits.last().copy(sort = emptyList())
            server.stubSearchRequest(malformedHits)

            shouldThrow<IllegalStateException> {
                sendTestRequest(LogSource.ADVISOR, EnumSet.of(LogLevel.ERROR))
            }
        }
    }
}

private suspend fun ElasticsearchLogFileProvider.testRequest(source: LogSource, levels: Set<LogLevel>): File {
    val folder = Files.createTempDirectory("elasticsearch-log-test").toFile().apply { deleteOnExit() }
    val logFile = downloadLogFile(RUN_ID, source, levels, START_TIME, END_TIME, folder, FILE_NAME)
    logFile.deleteOnExit()
    logFile.name shouldBe FILE_NAME
    return logFile
}

private fun List<ElasticsearchHit>.checkLogFile(logFile: File) {
    logFile.readLines() shouldBe mapNotNull { it.source.message }
}

private fun File.unpackTo(targetDirectory: File) {
    ZipFile(this).use { zipFile ->
        zipFile.entries().asSequence().forEach { entry ->
            val targetFile = targetDirectory.resolve(entry.name)
            if (entry.isDirectory) {
                targetFile.mkdirs()
            } else {
                targetFile.parentFile.mkdirs()
                zipFile.getInputStream(entry).use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
}

private fun WireMockServer.stubSearchRequest(
    hits: List<ElasticsearchHit>,
    searchAfter: List<JsonElement>? = null,
    source: LogSource = LogSource.ADVISOR,
    levels: Set<LogLevel> = setOf(LogLevel.ERROR)
) {
    stubFor(
        post(urlPathEqualTo("/$INDEX/_search")).withExpectedSearchBody(source, levels.toList(), searchAfter).willReturn(
            aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(generateSearchResponse(hits))
        )
    )
}

private fun MappingBuilder.withExpectedSearchBody(
    source: LogSource,
    levels: List<LogLevel>,
    searchAfter: List<JsonElement>? = null
): MappingBuilder =
    withRequestBody(matchingJsonPath("$.size", equalTo(LIMIT.toString())))
        .withRequestBody(matchingJsonPath("$._source[0]", equalTo("message")))
        .withRequestBody(matchingJsonPath("$.query.bool.filter[0].term.namespace", equalTo(NAMESPACE)))
        .withRequestBody(matchingJsonPath("$.query.bool.filter[1].term.component", equalTo(source.component)))
        .withRequestBody(matchingJsonPath("$.query.bool.filter[2].term.ortRunId", equalTo(RUN_ID.toString())))
        .withRequestBody(matchingJsonPath("$.query.bool.filter[4].range.time.gte", equalTo(START_TIME_MS.toString())))
        .withRequestBody(matchingJsonPath("$.query.bool.filter[4].range.time.lte", equalTo(END_TIME_MS.toString())))
        .withRequestBody(matchingJsonPath("$.query.bool.filter[4].range.time.format", equalTo("epoch_millis")))
        .withRequestBody(matchingJsonPath("$.sort[0].time.order", equalTo("asc")))
        .withRequestBody(matchingJsonPath("$.sort[1].sortId.order", equalTo("asc")))
        .withExpectedLevels(levels)
        .withExpectedSearchAfter(searchAfter)

private fun RequestPatternBuilder.withExpectedSearchBody(
    source: LogSource,
    levels: List<LogLevel>,
    searchAfter: List<JsonElement>? = null
): RequestPatternBuilder =
    withRequestBody(matchingJsonPath("$.size", equalTo(LIMIT.toString())))
        .withRequestBody(matchingJsonPath("$._source[0]", equalTo("message")))
        .withRequestBody(matchingJsonPath("$.query.bool.filter[0].term.namespace", equalTo(NAMESPACE)))
        .withRequestBody(matchingJsonPath("$.query.bool.filter[1].term.component", equalTo(source.component)))
        .withRequestBody(matchingJsonPath("$.query.bool.filter[2].term.ortRunId", equalTo(RUN_ID.toString())))
        .withRequestBody(matchingJsonPath("$.query.bool.filter[4].range.time.gte", equalTo(START_TIME_MS.toString())))
        .withRequestBody(matchingJsonPath("$.query.bool.filter[4].range.time.lte", equalTo(END_TIME_MS.toString())))
        .withRequestBody(matchingJsonPath("$.query.bool.filter[4].range.time.format", equalTo("epoch_millis")))
        .withRequestBody(matchingJsonPath("$.sort[0].time.order", equalTo("asc")))
        .withRequestBody(matchingJsonPath("$.sort[1].sortId.order", equalTo("asc")))
        .withExpectedLevels(levels)
        .withExpectedSearchAfter(searchAfter)

private fun MappingBuilder.withExpectedLevels(levels: List<LogLevel>): MappingBuilder =
    levels.foldIndexed(this) { index, request, level ->
        request.withRequestBody(matchingJsonPath("$.query.bool.filter[3].terms.level[$index]", equalTo(level.name)))
    }

private fun RequestPatternBuilder.withExpectedLevels(levels: List<LogLevel>): RequestPatternBuilder =
    levels.foldIndexed(this) { index, request, level ->
        request.withRequestBody(matchingJsonPath("$.query.bool.filter[3].terms.level[$index]", equalTo(level.name)))
    }

private fun MappingBuilder.withExpectedSearchAfter(searchAfter: List<JsonElement>?): MappingBuilder =
    searchAfter?.foldIndexed(this) { index, request, sortValue ->
        request.withRequestBody(matchingJsonPath("$.search_after[$index]", equalTo(sortValue.jsonPathValue())))
    } ?: this

private fun RequestPatternBuilder.withExpectedSearchAfter(searchAfter: List<JsonElement>?): RequestPatternBuilder =
    searchAfter?.foldIndexed(this) { index, request, sortValue ->
        request.withRequestBody(matchingJsonPath("$.search_after[$index]", equalTo(sortValue.jsonPathValue())))
    } ?: this

private fun JsonElement.jsonPathValue(): String =
    if (this is JsonPrimitive && isString) content else toString()

private fun WireMockServer.elasticsearchConfig(): ElasticsearchConfig =
    ElasticsearchConfig(
        serverUrl = "http://localhost:${port()}",
        index = INDEX,
        namespace = NAMESPACE,
        pageSize = LIMIT,
        username = null,
        password = null,
        apiKey = null
    )

private fun generateHits(count: Int, offset: Long = 0, source: LogSource = LogSource.ADVISOR): List<ElasticsearchHit> =
    List(count) { index ->
        val sortValue = offset + index + 1
        ElasticsearchHit(
            id = UUID.randomUUID().toString(),
            source = ElasticsearchSource(
                "time=2025-01-01T00:00:${"%02d".format(Locale.ROOT, index)}.000 component=${source.component} " +
                        "ortRunId=$RUN_ID level=ERROR message=\"log line $sortValue\""
            ),
            sort = listOf(JsonPrimitive(sortValue), JsonPrimitive("sort-id-$sortValue"))
        )
    }

private fun generateSearchResponse(hits: List<ElasticsearchHit>): String =
    JSON.encodeToString(ElasticsearchResponse(ElasticsearchHits(hits)))

private const val INDEX = "ort-server-logs-compose"
private const val NAMESPACE = "compose"
private const val LIMIT = 1000
private const val FILE_NAME = "test.log"
private const val RUN_ID = 2025L

private val JSON = Json { encodeDefaults = true }
private val START_TIME = Instant.parse("2025-01-01T00:00:00Z")
private val END_TIME = START_TIME + 60.seconds
private val START_TIME_MS = START_TIME.toEpochMilliseconds()
private val END_TIME_MS = END_TIME.toEpochMilliseconds()
