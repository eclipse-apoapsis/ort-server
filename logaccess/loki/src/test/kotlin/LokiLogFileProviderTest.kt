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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.BasicCredentials
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode

import io.mockk.mockk

import java.io.File
import java.io.IOException
import java.util.EnumSet
import java.util.Locale
import java.util.UUID

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.logaccess.LogFileService
import org.eclipse.apoapsis.ortserver.model.LogLevel
import org.eclipse.apoapsis.ortserver.model.LogSource

class LokiLogFileProviderTest : StringSpec() {
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

    init {
        "A correct log file should be returned" {
            val logData = generateLogData(32)
            server.stubLogRequest(logData, LogSource.ADVISOR, "ERROR")

            val logFile = sendTestRequest(LogSource.ADVISOR, EnumSet.of(LogLevel.ERROR))

            logData.checkLogFile(logFile)
        }

        "Log statements with identical timestamps should not cause an endless loop" {
            withTimeout(3.seconds) {
                val logData1 = generateLogData(LIMIT)
                val lastTimestamp = logData1.last().first
                server.stubLogRequest(logData1, LogSource.ADVISOR, "ERROR")

                val logData2 = generateLogDataWithIdenticalTimestamp(LIMIT, lastTimestamp)
                server.stubLogRequest(logData2, LogSource.ADVISOR, "ERROR", from = lastTimestamp)

                val logFile = sendTestRequest(LogSource.ADVISOR, EnumSet.of(LogLevel.ERROR))

                logData1.isContainedInLogFile(logFile) shouldBe true
                logData2.isContainedInLogFile(logFile) shouldBe true
            }
        }

        "Log data should be loaded in chunks and de-duplicated" {
            val logSize = LIMIT + 10
            val logData1 = generateLogData(LIMIT)
            val nextStartTimeStr = logData1.last().first
            val nextStartTime = logDataTimestamp(startTime, LIMIT)
            val logData2 = generateLogData(logSize - LIMIT + 1, nextStartTime)

            server.stubLogRequest(logData1, LogSource.EVALUATOR, "ERROR")
            server.stubLogRequest(
                logData1.takeLast(2) + logData2,
                LogSource.EVALUATOR,
                "ERROR",
                nextStartTimeStr
            )

            val logFile = sendTestRequest(LogSource.EVALUATOR, EnumSet.of(LogLevel.ERROR))

            val totalLogData = generateLogData(logSize)
            totalLogData.checkLogFile(logFile)
        }

        "Querying log data should terminate if only duplicate statements are returned" {
            val logData = generateLogData(LIMIT)
            server.stubLogRequest(logData, LogSource.ANALYZER, "ERROR")
            server.stubLogRequest(logData, LogSource.ANALYZER, "ERROR", logData.last().first)

            val logFile = sendTestRequest(LogSource.ANALYZER, EnumSet.of(LogLevel.ERROR))

            logData.checkLogFile(logFile)
        }

        "Multiple streams in the response should be handled" {
            val debugStartTime = startTime - 2.seconds + 17.milliseconds
            val logDataInfo = generateLogData(20, logLineGenerator = ::generateUniqueLogLine)
            val logDataDebug = generateLogData(44, debugStartTime, ::generateUniqueLogLine)
            val sortedData = logDataDebug.take(20) + buildList {
                logDataDebug.drop(20).zip(logDataInfo).forEach { (debug, info) ->
                    add(info)
                    add(debug)
                }
            } + logDataDebug.takeLast(4)

            val template = readResponseTemplate("loki-response-multi-streams.json.template")
            val infoResponse = logDataInfo.generateResponse(template, "<<log_values_info>>")
            val response = logDataDebug.generateResponse(infoResponse, "<<log_values_debug>>")
            server.stubFor(
                get(urlPathEqualTo("/loki/api/v1/query_range"))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(response)
                    )
            )

            val logFile = sendTestRequest(LogSource.ANALYZER, EnumSet.of(LogLevel.ERROR))

            sortedData.checkLogFile(logFile)
        }

        "The log level should correctly be evaluated" {
            val logData = generateLogData(8)
            val levelCriterion = "INFO|WARN"
            server.stubLogRequest(logData, LogSource.REPORTER, levelCriterion)

            val logFile = sendTestRequest(LogSource.REPORTER, EnumSet.of(LogLevel.INFO, LogLevel.WARN))

            logData.checkLogFile(logFile)
        }

        "Failed requests should be handled" {
            server.stubFor(
                get(anyUrl())
                    .willReturn(aResponse().withStatus(HttpStatusCode.InternalServerError.value))
            )

            val exception = shouldThrow<ServerResponseException> {
                sendTestRequest(LogSource.SCANNER, EnumSet.of(LogLevel.DEBUG))
            }

            exception.response.status shouldBe HttpStatusCode.InternalServerError
        }

        "Basic Auth should be supported" {
            val logData = generateLogData(16)
            server.stubLogRequest(logData, LogSource.ADVISOR, "ERROR")

            val username = "scott"
            val password = "tiger"
            val config = server.lokiConfig().copy(username = username, password = password)
            val provider = LokiLogFileProvider(config)

            val logFile = provider.testRequest(LogSource.ADVISOR, EnumSet.of(LogLevel.ERROR))

            logData.checkLogFile(logFile)

            server.verify(
                getRequestedFor(urlPathEqualTo("/loki/api/v1/query_range"))
                    .withBasicAuth(BasicCredentials(username, password))
            )
        }

        "Multi-tenant mode should be supported" {
            val logData = generateLogData(16)
            server.stubLogRequest(logData, LogSource.ADVISOR, "ERROR")

            val tenant = "lokiTestTenant"
            val config = server.lokiConfig().copy(tenantId = tenant)
            val provider = LokiLogFileProvider(config)

            val logFile = provider.testRequest(LogSource.ADVISOR, EnumSet.of(LogLevel.ERROR))

            logData.checkLogFile(logFile)

            server.verify(
                getRequestedFor(urlPathEqualTo("/loki/api/v1/query_range"))
                    .withHeader("X-Scope-OrgID", equalTo(tenant))
            )
        }

        "The timeout setting is evaluated" {
            val response = generateLogData(16).generateResponse()
            server.stubFor(
                get(urlPathEqualTo("/loki/api/v1/query_range"))
                    .willReturn(
                        aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(response)
                            .withFixedDelay(2000)
                    )
            )

            val config = server.lokiConfig().copy(timeoutSec = 1)
            val provider = LokiLogFileProvider(config)

            shouldThrow<IOException> {
                provider.testRequest(LogSource.ADVISOR, EnumSet.of(LogLevel.ERROR))
            }
        }

        "The integration with LogFileService should work" {
            val lokiConfig = server.lokiConfig()
            val providerConfigMap = mapOf(
                "name" to "loki",
                "lokiServerUrl" to lokiConfig.serverUrl,
                "lokiNamespace" to lokiConfig.namespace,
                "lokiQueryLimit" to lokiConfig.limit
            )
            val configMap = mapOf(LogFileService.LOG_FILE_SERVICE_SECTION to providerConfigMap)

            val configManager = ConfigManager(
                ConfigFactory.parseMap(configMap),
                { mockk() },
                { mockk() },
                allowSecretsFromConfig = false
            )

            val logFileService = LogFileService.create(configManager, tempdir().toPath())

            val logData = generateLogData(32)
            server.stubLogRequest(logData, LogSource.ADVISOR, "ERROR")

            logFileService.createLogFilesArchive(
                RUN_ID,
                EnumSet.of(LogSource.ADVISOR),
                LogLevel.ERROR,
                startTime,
                endTime
            )

            server.verify(getRequestedFor(urlPathEqualTo("/loki/api/v1/query_range")))
        }
    }

    /**
     * Send a test request for the given [source] and [levels] and return the resulting log file.
     */
    private suspend fun sendTestRequest(source: LogSource, levels: Set<LogLevel>): File =
        LokiLogFileProvider(server.lokiConfig()).testRequest(source, levels)

    /**
     * Trigger sending a test request via this [LokiLogFileProvider] for the given [source] and [levels].
     */
    private suspend fun LokiLogFileProvider.testRequest(source: LogSource, levels: Set<LogLevel>): File {
        val folder = tempdir()
        val logFile = downloadLogFile(RUN_ID, source, levels, startTime, endTime, folder, FILE_NAME)

        logFile.parentFile shouldBe folder
        logFile.name shouldBe FILE_NAME

        return logFile
    }
}

/**
 * Type definition for log data returned by Loki. This is a list of pairs consisting of a timestamp and a log
 * statement.
 */
private typealias LogData = List<Pair<String, String>>

private val startTime = Instant.parse("2023-11-20T08:28:17.123Z")
private val endTime = Instant.parse("2023-11-20T08:30:45.987Z")
private const val START_TIME_STR = "1700468897"
private const val END_TIME_STR = "1700469046"
private const val NAMESPACE = "ort_server_ns"
private const val FILE_NAME = "result.log"
private const val LIMIT = 100
private const val RUN_ID = 20231120152344L

private const val LOG_VALUES_PLACEHOLDER = "<<log_values>>"
private const val DEFAULT_RESPONSE_TEMPLATE = "loki-response.json.template"

/**
 * A template to generate responses of the Loki server. The template contains the overall JSON structure of the
 * response. The actual log statements can be inserted dynamically.
 */
private val responseTemplate = readResponseTemplate()

/**
 * Return the URL of this mock server to be used in the [LokiConfig].
 */
private fun WireMockServer.lokiUrl() = "http://localhost:${port()}"

/**
 * Return a default [LokiConfig] that references this mock server.
 */
private fun WireMockServer.lokiConfig(): LokiConfig =
    LokiConfig(
        serverUrl = lokiUrl(),
        namespace = NAMESPACE,
        limit = LIMIT,
        username = null,
        password = null
    )

/**
 * Prepare this server to expect a request for log data for the given [source], [levelCriterion] and
 * [start time][from]. As response, return the given [data].
 */
private fun WireMockServer.stubLogRequest(
    data: LogData,
    source: LogSource,
    levelCriterion: String,
    from: String = START_TIME_STR
) {
    val response = data.generateResponse()

    stubFor(
        get(urlPathEqualTo("/loki/api/v1/query_range"))
            .withQueryParam("end", equalTo(END_TIME_STR))
            .withQueryParam("start", equalTo(from))
            .withQueryParam("limit", equalTo(LIMIT.toString()))
            .withQueryParam("direction", equalTo("forward"))
            .withQueryParam("query", equalTo(generateQuery(source, levelCriterion)))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(response)
            )
    )
}

/**
 * Read a file with the given [name] containing a template to generate Loki responses with log statements.
 */
private fun readResponseTemplate(name: String = DEFAULT_RESPONSE_TEMPLATE): String =
    LokiLogFileProviderTest::class.java.getResource("/$name")?.readText()
        ?: fail("Could not load response template '$name'.")

/**
 * Generate a log line for the given [timestamp].
 */
private fun generateLogLine(timestamp: Instant): String =
    "[$timestamp] Something interesting just happened at this time."

/**
 * Generate a unique log line for the given [timestamp].
 * Uniqueness is achieved by adding a random UUID to the log line.
 */
private fun generateUniqueLogLine(timestamp: Instant): String =
    "[$timestamp] ${UUID.randomUUID()} Something interesting just happened at this time."

/**
 * Generate log values for a Loki query response based on the given parameters. Generate [count] values starting at
 * [from] with a delta of 100ms between two values. Use the given [logLineGenerator] function to generate the log
 * lines at the single points in time. The resulting pairs contain the timestamp as string as first element and the
 * generated log line as second element.
 */
private fun generateLogData(
    count: Int,
    from: Instant = startTime,
    logLineGenerator: (Instant) -> String = ::generateLogLine
): LogData =
    (1..count).map { index ->
        val timestamp = logDataTimestamp(from, index)
        timestamp.toNanoStr() to logLineGenerator(timestamp)
    }

/**
 * Generate [count] log values for a Loki query response. All log values have the same timestamp.
 * To make the log lines unique, a random UUID is added to each log line.
 */
private fun generateLogDataWithIdenticalTimestamp(count: Int, timestamp: String): LogData {
    val instant = Instant.fromEpochMilliseconds(timestamp.toLong() / 1_000_000)
    return List(count) { timestamp to generateUniqueLogLine(instant) }
}

/**
 * Generate the timestamp of a test log entry starting at [from] with the given [index].
 */
private fun logDataTimestamp(from: Instant, index: Int): Instant = from.plus(((index - 1) * 100).milliseconds)

/**
 * Convert this [Instant] to a string with epoch nanoseconds. This format is used by Loki.
 */
private fun Instant.toNanoStr(): String =
    String.format(Locale.ROOT, "%d%09d", epochSeconds, nanosecondsOfSecond)

/**
 * Generate a response for the Loki server that contains this [LogData] using the given [template] and [placeholder].
 */
private fun LogData.generateResponse(
    template: String = responseTemplate,
    placeholder: String = LOG_VALUES_PLACEHOLDER
): String {
    val logDataJson = joinToString(",") { data ->
        """
            |[
            |  "${data.first}",
            |  "${data.second}"
            |]
            """.trimMargin()
    }

    return template.replace(placeholder, logDataJson)
}

/**
 * Check whether the given [file] contains the log statements represented by this [LogData].
 */
private fun LogData.checkLogFile(file: File) {
    val expectedLogLines = map { it.second }
    val logLines = file.readLines()

    logLines shouldBe expectedLogLines
}

private fun LogData.isContainedInLogFile(file: File): Boolean {
    val logLines = file.readLines()
    return logLines.containsAll(map { it.second })
}

/**
 * Generate a query string for the given [source] and [log level criterion][levelCriterion].
 */
private fun generateQuery(source: LogSource, levelCriterion: String): String =
    """{namespace="$NAMESPACE",component="${source.component}"} |~ "level=$levelCriterion" |~ "ortRunId=$RUN_ID""""
