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

package org.eclipse.apoapsis.ortserver.core.api

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.fail
import io.kotest.assertions.ktor.client.haveHeader
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.engine.spec.tempdir
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo

import java.io.File
import java.io.IOException
import java.util.EnumSet

import kotlin.time.Duration.Companion.minutes

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.PagedResponse
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityWithIdentifier
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.core.shouldHaveBody
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.logaccess.LogFileCriteria
import org.eclipse.apoapsis.ortserver.logaccess.LogFileProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.logaccess.LogLevel
import org.eclipse.apoapsis.ortserver.logaccess.LogSource
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.PluginConfiguration
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.authorization.RepositoryPermission
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorResult
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Vulnerability
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.model.util.asPresent
import org.eclipse.apoapsis.ortserver.services.DefaultAuthorizationService
import org.eclipse.apoapsis.ortserver.services.OrganizationService
import org.eclipse.apoapsis.ortserver.services.ProductService
import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.utils.test.Integration

import org.ossreviewtoolkit.utils.common.ArchiveType
import org.ossreviewtoolkit.utils.common.unpack

class RunsRouteIntegrationTest : AbstractIntegrationTest({
    tags(Integration)

    lateinit var ortRunRepository: OrtRunRepository

    var repositoryId = -1L

    beforeEach {
        val authorizationService = DefaultAuthorizationService(
            keycloakClient,
            dbExtension.db,
            dbExtension.fixtures.organizationRepository,
            dbExtension.fixtures.productRepository,
            dbExtension.fixtures.repositoryRepository,
            keycloakGroupPrefix = ""
        )

        val organizationService = OrganizationService(
            dbExtension.db,
            dbExtension.fixtures.organizationRepository,
            dbExtension.fixtures.productRepository,
            authorizationService
        )

        val productService = ProductService(
            dbExtension.db,
            dbExtension.fixtures.productRepository,
            dbExtension.fixtures.repositoryRepository,
            authorizationService
        )

        ortRunRepository = dbExtension.fixtures.ortRunRepository

        val orgId = organizationService.createOrganization(name = "name", description = "description").id
        val productId =
            organizationService.createProduct(name = "name", description = "description", organizationId = orgId).id
        repositoryId = productService.createRepository(
            type = RepositoryType.GIT,
            url = "https://example.org/repo.git",
            productId = productId
        ).id

        LogFileProviderFactoryForTesting.reset()
    }

    val labelsMap = mapOf("label1" to "value1", "label2" to "value2")
    val reportFile = "disclosure-document-pdf"
    val reportData = "Data of the report to download".toByteArray()

    /**
     * Create an [OrtRun], store a report for the created run, and return the created run.
     */
    fun createReport(): OrtRun {
        val run = dbExtension.fixtures.createOrtRun(repositoryId)
        val key = Key("${run.id}|$reportFile")

        val storage = Storage.create("reportStorage", ConfigManager.create(ConfigFactory.load("application-test.conf")))
        storage.write(key, reportData, "application/pdf")

        val reporterJob = dbExtension.fixtures.createReporterJob(ortRunId = run.id)
        val report = Report(reportFile, "", Instant.fromEpochSeconds(0))
        dbExtension.fixtures.reporterRunRepository.create(
            reporterJob.id,
            Clock.System.now() - 1.minutes,
            Clock.System.now(),
            listOf(report)
        )

        return run
    }

    /**
     * Create an [OrtRun] that can be used for a test for downloading log files. Log files for this run are
     * generated and added to the test log access provider expecting that they are queried for the given [levels].
     * By default, the run can be marked as [completed][complete].
     */
    fun prepareLogTest(levels: Set<LogLevel>, complete: Boolean = true): OrtRun {
        val logFileDir = tempdir()
        val run = dbExtension.fixtures.createOrtRun(repositoryId)
        val updatedRun = if (complete) {
            dbExtension.fixtures.ortRunRepository.update(run.id, status = OrtRunStatus.FINISHED.asPresent())
        } else {
            run
        }

        LogSource.entries.forEach { source ->
            val logFile = logFileDir.resolve("${source.name}.log")
            logFile.writeText(logFileContent(source))

            val logCriteria = LogFileCriteria(
                ortRunId = run.id,
                source = source,
                levels = levels,
                startTime = updatedRun.createdAt,
                endTime = updatedRun.finishedAt
            )
            LogFileProviderFactoryForTesting.addLogFile(logCriteria, logFile)
        }

        return updatedRun
    }

    /**
     * Check the given [response] of a request to download a log file. In case of a failure, generate a helpful
     * error message. Otherwise, return the channel for downloading the archive file.
     */
    suspend fun checkLogFileResponse(response: HttpResponse): ByteReadChannel {
        if (!response.status.isSuccess()) {
            fail("Request failed: ${response.status} - ${response.body<ErrorResponse>()}")
        }

        return response.bodyAsChannel()
    }

    /**
     * Check whether the given [channel] from the response to download a log archive contains the log files for the
     * given [sources].
     */
    suspend fun checkLogArchiveChannel(channel: ByteReadChannel, sources: Set<LogSource>) {
        val downloadDir = tempdir()
        val downloadFile = downloadDir.resolve("logs.zip")

        downloadFile.outputStream().use { out ->
            channel.copyTo(out)
        }

        checkLogArchive(downloadFile, sources)
    }

    "GET /runs/{runId}" should {
        "return the requested ORT run" {
            integrationTestApplication {
                val run = ortRunRepository.create(
                    repositoryId,
                    "revision",
                    null,
                    JobConfigurations(),
                    "jobConfigContext",
                    labelsMap,
                    traceId = "t1"
                )

                val response = superuserClient.get("/api/v1/runs/${run.id}")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody run.mapToApi(Jobs())
            }
        }

        "include job details" {
            integrationTestApplication {
                val run = ortRunRepository.create(
                    repositoryId,
                    "revision",
                    null,
                    JobConfigurations(),
                    "testContext",
                    labelsMap,
                    traceId = "trace"
                )

                val jobs = dbExtension.fixtures.createJobs(run.id).mapToApi()

                val response = superuserClient.get("/api/v1/runs/${run.id}")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody run.mapToApi(jobs)
            }
        }

        "require RepositoryPermission.READ_ORT_RUNS" {
            val run = ortRunRepository.create(
                repositoryId,
                "revision",
                null,
                JobConfigurations(),
                null,
                labelsMap,
                traceId = "test-trace-id"
            )

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(repositoryId)) {
                get("/api/v1/runs/${run.id}")
            }
        }
    }

    "GET /runs/{runId}/logs/" should {
        "download an archive with all logs" {
            integrationTestApplication {
                val run = prepareLogTest(EnumSet.of(LogLevel.ERROR, LogLevel.WARN, LogLevel.INFO))

                val channel = checkLogFileResponse(superuserClient.get("/api/v1/runs/${run.id}/logs"))

                checkLogArchiveChannel(channel, EnumSet.allOf(LogSource::class.java))
            }
        }

        "set correct response headers" {
            integrationTestApplication {
                val run = prepareLogTest(EnumSet.of(LogLevel.ERROR, LogLevel.WARN, LogLevel.INFO))

                val response = superuserClient.get("/api/v1/runs/${run.id}/logs")

                val expectedContentDispositionHeader = "attachment; filename=run-${run.id}-INFO-logs.zip"
                response.headers["Content-Disposition"] shouldBe expectedContentDispositionHeader
                response.headers["Content-Type"] shouldBe "application/zip"
            }
        }

        "handle a non-existing ORT run" {
            integrationTestApplication {
                val response = superuserClient.get("/api/v1/runs/12345/logs")

                response shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "handle a run that is not yet complete" {
            integrationTestApplication {
                val run = prepareLogTest(EnumSet.of(LogLevel.ERROR, LogLevel.WARN, LogLevel.INFO), complete = false)

                val channel = checkLogFileResponse(superuserClient.get("/api/v1/runs/${run.id}/logs"))

                checkLogArchiveChannel(channel, EnumSet.allOf(LogSource::class.java))

                val logRequests = LogFileProviderFactoryForTesting.requests()
                logRequests shouldHaveSize LogSource.entries.size
                logRequests.forAll { request ->
                    val endTime = request.endTime.shouldNotBeNull()
                    val delta = Clock.System.now() - endTime
                    delta.inWholeMilliseconds shouldBeGreaterThan 0
                    delta.inWholeMilliseconds shouldBeLessThan 10000
                }
            }
        }

        "support specifying the log level" {
            integrationTestApplication {
                val run = prepareLogTest(EnumSet.of(LogLevel.ERROR, LogLevel.WARN))

                val channel = checkLogFileResponse(superuserClient.get("/api/v1/runs/${run.id}/logs?level=Warn"))

                checkLogArchiveChannel(channel, EnumSet.allOf(LogSource::class.java))
            }
        }

        "handle an invalid log level" {
            val invalidLevel = "i_want_it_all"

            integrationTestApplication {
                val run = prepareLogTest(EnumSet.of(LogLevel.ERROR, LogLevel.WARN))

                val response = superuserClient.get("/api/v1/runs/${run.id}/logs?level=$invalidLevel")

                response.status shouldBe HttpStatusCode.BadRequest
                val cause = response.body<ErrorResponse>().cause
                cause shouldContain invalidLevel
                cause shouldContain "INFO"
            }
        }

        "support specifying log sources" {
            integrationTestApplication {
                val run = prepareLogTest(EnumSet.of(LogLevel.ERROR, LogLevel.WARN, LogLevel.INFO))

                val channel = checkLogFileResponse(
                    superuserClient.get("/api/v1/runs/${run.id}/logs?steps=Analyzer,scanner,REPORTER")
                )

                checkLogArchiveChannel(channel, EnumSet.of(LogSource.ANALYZER, LogSource.SCANNER, LogSource.REPORTER))
            }
        }

        "handle an invalid step" {
            val invalidStep = "nonExistingStep"

            integrationTestApplication {
                val run = prepareLogTest(EnumSet.of(LogLevel.ERROR, LogLevel.WARN, LogLevel.INFO))

                val response = superuserClient.get("/api/v1/runs/${run.id}/logs?steps=Analyzer,$invalidStep")

                response.status shouldBe HttpStatusCode.BadRequest
                val cause = response.body<ErrorResponse>().cause
                cause shouldContain invalidStep
                cause shouldContain "ANALYZER"
            }
        }

        "require RepositoryPermission.READ_ORT_RUNS" {
            val run = prepareLogTest(EnumSet.of(LogLevel.ERROR, LogLevel.WARN, LogLevel.INFO))

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(repositoryId)) {
                get("/api/v1/runs/${run.id}/logs")
            }
        }
    }

    "GET /runs/{runId}/reporter/{fileName}" should {
        "download a report" {
            integrationTestApplication {
                val run = createReport()

                val response = superuserClient.get("/api/v1/runs/${run.id}/reporter/$reportFile")

                response shouldHaveStatus HttpStatusCode.OK
                response should haveHeader("Content-Type", "application/pdf")
                response.body<ByteArray>() shouldBe reportData
            }
        }

        "handle a missing report" {
            integrationTestApplication {
                val missingReportFile = "nonExistingReport.pdf"
                val run = dbExtension.fixtures.createOrtRun(repositoryId)

                val response = superuserClient.get("/api/v1/runs/${run.id}/reporter/$missingReportFile")

                response shouldHaveStatus HttpStatusCode.NotFound
                response.body<ErrorResponse>().cause shouldContain missingReportFile
            }
        }

        "respond with NotFound if the ORT run does not exist" {
            integrationTestApplication {
                superuserClient.get("/api/v1/runs/999/reporter/report.pdf") shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "require RepositoryPermission.READ_ORT_RUNS" {
            val run = createReport()

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(repositoryId)) {
                get("/api/v1/runs/${run.id}/reporter/$reportFile")
            }
        }
    }

    "GET /runs/{runId}/vulnerabilities" should {
        "show the vulnerabilities of an ORT run" {
            integrationTestApplication {
                val ortRun = dbExtension.fixtures.createOrtRun(
                    repositoryId = repositoryId,
                    revision = "revision",
                    jobConfigurations = JobConfigurations()
                )

                val advisorJob = dbExtension.fixtures.createAdvisorJob(
                    ortRunId = ortRun.id,
                    configuration = AdvisorJobConfiguration(
                        config = mapOf(
                            "VulnerableCode" to PluginConfiguration(
                                options = mapOf("serverUrl" to "https://public.vulnerablecode.io"),
                                secrets = mapOf("apiKey" to "key")
                            )
                        )
                    )
                )

                dbExtension.fixtures.advisorRunRepository.create(
                    advisorJobId = advisorJob.id,
                    startTime = Clock.System.now().toDatabasePrecision(),
                    endTime = Clock.System.now().toDatabasePrecision(),
                    environment = Environment(
                        ortVersion = "1.0",
                        javaVersion = "11.0.16",
                        os = "Linux",
                        processors = 8,
                        maxMemory = 8321499136,
                        variables = emptyMap(),
                        toolVersions = emptyMap()
                    ),
                    config = AdvisorConfiguration(
                        config = mapOf(
                            "VulnerableCode" to PluginConfiguration(
                                options = mapOf("serverUrl" to "https://public.vulnerablecode.io"),
                                secrets = mapOf("apiKey" to "key")
                            )
                        )
                    ),
                    results = mapOf(
                        Identifier("Maven", "org.apache.logging.log4j", "log4j-core", "2.14.0") to listOf(
                            AdvisorResult(
                                advisorName = "advisor",
                                capabilities = listOf("vulnerabilities"),
                                startTime = Clock.System.now().toDatabasePrecision(),
                                endTime = Clock.System.now().toDatabasePrecision(),
                                issues = emptyList(),
                                defects = emptyList(),
                                vulnerabilities = listOf(
                                    Vulnerability(
                                        externalId = "CVE-2021-1234",
                                        summary = "A vulnerability",
                                        description = "A description",
                                        references = emptyList()
                                    )
                                )
                            )
                        )
                    ),
                )

                val response = superuserClient.get("/api/v1/runs/${ortRun.id}/vulnerabilities")

                response.status shouldBe HttpStatusCode.OK
                val vulnerabilities = response.body<PagedResponse<VulnerabilityWithIdentifier>>()

                with(vulnerabilities.data) {
                    shouldHaveSize(1)
                    first().vulnerability.externalId shouldBe "CVE-2021-1234"

                    with(first().identifier) {
                        type shouldBe "Maven"
                        namespace shouldBe "org.apache.logging.log4j"
                        name shouldBe "log4j-core"
                        version shouldBe "2.14.0"
                    }
                }
            }
        }

        "require RepositoryPermission.READ_ORT_RUNS" {
            val run = ortRunRepository.create(
                repositoryId,
                "revision",
                null,
                JobConfigurations(),
                null,
                labelsMap,
                traceId = "test-trace-id"
            )

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(repositoryId)) {
                get("/api/v1/runs/${run.id}/vulnerabilities")
            }
        }
    }
})

/**
 * Generate the content of a log file for the given [source].
 */
private fun logFileContent(source: LogSource): String =
    "Content of log file for '${source.name}'."

/**
 * Check whether the given [archiveFile] contains the correct logs for the given [sources].
 */
private fun checkLogArchive(archiveFile: File, sources: Set<LogSource>) {
    archiveFile.isFile shouldBe true
    val currentDir = archiveFile.parentFile

    try {
        archiveFile.unpack(targetDirectory = currentDir, forceArchiveType = ArchiveType.ZIP)

        val files = currentDir.walk().maxDepth(1).filter { it.isFile }.mapTo(mutableListOf()) { it.name }
        val expectedFileNames = sources.map { it.logFileName() } + archiveFile.name
        files shouldContainExactlyInAnyOrder expectedFileNames

        sources.forAll { source ->
            val logFile = currentDir.resolve(source.logFileName())
            logFile.readText() shouldBe logFileContent(source)
        }
    } catch (e: IOException) {
        val content = archiveFile.readText()
        fail("Could not unpack log archive: ${e.message}.\nFile content is:\n$content")
    }
}

/**
 * Return the name of the log file for this [LogSource].
 */
private fun LogSource.logFileName() = "${name.lowercase()}.log"
