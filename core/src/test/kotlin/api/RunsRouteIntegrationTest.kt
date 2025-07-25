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
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.file.aFile
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.ktor.client.call.body
import io.ktor.client.request.delete
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

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApiSummary
import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToModel
import org.eclipse.apoapsis.ortserver.api.v1.model.ComparisonOperator
import org.eclipse.apoapsis.ortserver.api.v1.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.api.v1.model.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier as ApiIdentifier
import org.eclipse.apoapsis.ortserver.api.v1.model.Issue as ApiIssue
import org.eclipse.apoapsis.ortserver.api.v1.model.JobSummaries
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.Licenses
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun as ApiOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunFilters
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatistics
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus as ApiOrtRunStatus
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunSummary
import org.eclipse.apoapsis.ortserver.api.v1.model.Package as ApiPackage
import org.eclipse.apoapsis.ortserver.api.v1.model.PackageFilters
import org.eclipse.apoapsis.ortserver.api.v1.model.Project as ApiProject
import org.eclipse.apoapsis.ortserver.api.v1.model.RuleViolation
import org.eclipse.apoapsis.ortserver.api.v1.model.Severity as ApiSeverity
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityRating
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityWithIdentifier
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.roles.Superuser
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.logaccess.LogFileCriteria
import org.eclipse.apoapsis.ortserver.logaccess.LogFileProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.LogLevel
import org.eclipse.apoapsis.ortserver.model.LogSource
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.PluginConfig
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.UserDisplayName
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.OrtRuleViolation
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.model.runs.Project
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.ShortestDependencyPath
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorResult
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Vulnerability
import org.eclipse.apoapsis.ortserver.model.runs.advisor.VulnerabilityReference
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.model.runs.repository.Resolutions
import org.eclipse.apoapsis.ortserver.model.runs.repository.VulnerabilityResolution
import org.eclipse.apoapsis.ortserver.model.util.asPresent
import org.eclipse.apoapsis.ortserver.services.DefaultAuthorizationService
import org.eclipse.apoapsis.ortserver.services.OrganizationService
import org.eclipse.apoapsis.ortserver.services.ProductService
import org.eclipse.apoapsis.ortserver.shared.apimodel.ErrorResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedSearchResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.ktorutils.shouldHaveBody
import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.utils.test.Integration

import org.ossreviewtoolkit.utils.common.ArchiveType
import org.ossreviewtoolkit.utils.common.unpack

@Suppress("LargeClass")
class RunsRouteIntegrationTest : AbstractIntegrationTest({
    tags(Integration)

    lateinit var ortRunRepository: OrtRunRepository
    lateinit var organizationService: OrganizationService
    lateinit var productService: ProductService

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

        organizationService = OrganizationService(
            dbExtension.db,
            dbExtension.fixtures.organizationRepository,
            dbExtension.fixtures.productRepository,
            authorizationService
        )

        productService = ProductService(
            dbExtension.db,
            dbExtension.fixtures.productRepository,
            dbExtension.fixtures.repositoryRepository,
            dbExtension.fixtures.ortRunRepository,
            authorizationService
        )

        ortRunRepository = dbExtension.fixtures.ortRunRepository

        val orgId = organizationService.createOrganization(name = "name", description = "description").id
        val productId =
            organizationService.createProduct(name = "name", description = "description", organizationId = orgId).id
        repositoryId = productService.createRepository(
            type = RepositoryType.GIT,
            url = "https://example.org/repo.git",
            productId = productId,
            description = "description"
        ).id

        LogFileProviderFactoryForTesting.reset()
    }

    val labelsMap = mapOf("label1" to "value1", "label2" to "value2")
    val reportFile = "disclosure-document-pdf"
    val reportData = "Data of the report to download".toByteArray()

    /**
     * Create an [OrtRun], store a report for the created run, and return the created run.
     */
    suspend fun createReport(): OrtRun {
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
                    traceId = "t1",
                    null
                )

                val issue = ApiIssue(
                    timestamp = Clock.System.now().toDatabasePrecision(),
                    source = "Integration-Test",
                    message = "This is a test issue",
                    severity = ApiSeverity.WARNING,
                    affectedPath = "test/path",
                    identifier = ApiIdentifier("test", "test-ns", "test-name", "test-version"),
                    worker = "Analyzer"
                )
                ortRunRepository.update(run.id, issues = listOf(issue.mapToModel()).asPresent())

                val response = superuserClient.get("/api/v1/runs/${run.id}")

                response shouldHaveStatus HttpStatusCode.OK
                val expectedBody = run.mapToApi(Jobs()).copy(issues = listOf(issue))
                response shouldHaveBody expectedBody
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
                    traceId = "trace",
                    null
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
                traceId = "test-trace-id",
                null
            )

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(repositoryId)) {
                get("/api/v1/runs/${run.id}")
            }
        }

        "include name of the user that triggered this run" {
            val userDisplayName = UserDisplayName("user-id", "username", "full name")
            integrationTestApplication {
                val run = ortRunRepository.create(
                    repositoryId,
                    "revision",
                    null,
                    JobConfigurations(),
                    "testContext",
                    labelsMap,
                    traceId = "trace",
                    null,
                    userDisplayName
                )

                val response = superuserClient.get("/api/v1/runs/${run.id}")

                response shouldHaveStatus HttpStatusCode.OK

                val ortRun = response.body<ApiOrtRun>()
                ortRun shouldNotBeNull {
                    this.userDisplayName shouldNotBeNull {
                        this.username shouldBe userDisplayName.username
                        this.fullName shouldBe userDisplayName.fullName
                    }
                }
            }
        }

        "update the name of the user if user already exists" {
            val userDisplayName1 = UserDisplayName("user-id", "username", "full name")
            val userDisplayName2 = UserDisplayName("user-id", "new-username", "new full name")
            integrationTestApplication {
                val run1 = ortRunRepository.create(
                    repositoryId,
                    "revision",
                    null,
                    JobConfigurations(),
                    "testContext",
                    labelsMap,
                    traceId = "trace",
                    null,
                    userDisplayName1
                )

                val response1 = superuserClient.get("/api/v1/runs/${run1.id}")
                response1 shouldHaveStatus HttpStatusCode.OK

                // UserDisplayName is already created by the previous request, now do another request with different
                // username and full name and check if it is updated.
                val run2 = ortRunRepository.create(
                    repositoryId,
                    "revision",
                    null,
                    JobConfigurations(),
                    "testContext",
                    labelsMap,
                    traceId = "trace",
                    null,
                    userDisplayName2
                )

                val response2 = superuserClient.get("/api/v1/runs/${run2.id}")
                response2 shouldHaveStatus HttpStatusCode.OK

                val ortRun2 = response2.body<ApiOrtRun>()
                ortRun2 shouldNotBeNull {
                    this.userDisplayName shouldNotBeNull {
                        this.username shouldBe userDisplayName2.username
                        this.fullName shouldBe userDisplayName2.fullName
                    }
                }
            }
        }
    }

    "DELETE /runs/{runId}" should {
        "should require role RepositoryPermission.DELETE" {
            val run = ortRunRepository.create(
                repositoryId,
                "revision",
                null,
                JobConfigurations(),
                "jobConfigContext",
                labelsMap,
                traceId = "t1",
                null
            )

            requestShouldRequireRole(RepositoryPermission.DELETE.roleName(repositoryId), HttpStatusCode.NoContent) {
                delete("/api/v1/runs/${run.id}")
            }
        }

        "handle a non-existing ORT run" {
            integrationTestApplication {
                val response = superuserClient.delete("/api/v1/runs/12345")

                response shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "delete an ORT run" {
            val run = ortRunRepository.create(
                repositoryId,
                "revision",
                null,
                JobConfigurations(),
                "jobConfigContext",
                labelsMap,
                traceId = "t1",
                null
            )

            integrationTestApplication {
                val response = superuserClient.delete("/api/v1/runs/${run.id}")

                response shouldHaveStatus HttpStatusCode.NoContent

                ortRunRepository.get(run.id) shouldBe beNull()
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

                response shouldHaveStatus HttpStatusCode.BadRequest
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

                response shouldHaveStatus HttpStatusCode.BadRequest
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
                            "VulnerableCode" to PluginConfig(
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
                    environment = generateAdvisorEnvironment(),
                    config = generateAdvisorConfiguration(),
                    results = generateAdvisorResult()
                )

                val response = superuserClient.get("/api/v1/runs/${ortRun.id}/vulnerabilities")

                response shouldHaveStatus HttpStatusCode.OK
                val vulnerabilities = response.body<PagedResponse<VulnerabilityWithIdentifier>>()

                vulnerabilities.data shouldHaveSize 2

                with(vulnerabilities.data[0]) {
                    vulnerability.externalId shouldBe "CVE-2018-14721"
                    rating shouldBe VulnerabilityRating.MEDIUM

                    with(identifier) {
                        type shouldBe "Maven"
                        namespace shouldBe "com.fasterxml.jackson.core"
                        name shouldBe "jackson-databind"
                        version shouldBe "2.9.6"
                    }
                }

                with(vulnerabilities.data[1]) {
                    vulnerability.externalId shouldBe "CVE-2021-1234"
                    rating shouldBe VulnerabilityRating.MEDIUM

                    with(identifier) {
                        type shouldBe "Maven"
                        namespace shouldBe "org.apache.logging.log4j"
                        name shouldBe "log4j-core"
                        version shouldBe "2.14.0"
                    }
                }
            }
        }

        "show only the resolved vulnerabilities of an ORT run" {
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
                            "VulnerableCode" to PluginConfig(
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
                    environment = generateAdvisorEnvironment(),
                    config = generateAdvisorConfiguration(),
                    results = generateAdvisorResult()
                )

                // Add a resolution for one of the vulnerabilities.
                val vulnerabilityResolution = VulnerabilityResolution(
                    externalId = ".*CVE-2021-12.*", // Matching RegEx
                    reason = "INEFFECTIVE_VULNERABILITY",
                    comment = "This is ineffective because of some reasons."
                )
                dbExtension.fixtures.resolvedConfigurationRepository.addResolutions(
                    ortRun.id,
                    Resolutions(vulnerabilities = listOf(vulnerabilityResolution))
                )

                val response = superuserClient.get("/api/v1/runs/${ortRun.id}/vulnerabilities?resolved=true")

                response shouldHaveStatus HttpStatusCode.OK
                val vulnerabilities = response.body<PagedResponse<VulnerabilityWithIdentifier>>()

                with(vulnerabilities.data) {
                    shouldHaveSize(1)
                    first().vulnerability.externalId shouldBe "CVE-2021-1234"
                    first().rating shouldBe VulnerabilityRating.MEDIUM

                    with(first().identifier) {
                        type shouldBe "Maven"
                        namespace shouldBe "org.apache.logging.log4j"
                        name shouldBe "log4j-core"
                        version shouldBe "2.14.0"
                    }

                    with(first().resolutions.first()) {
                        externalId shouldBe ".*CVE-2021-12.*" // Matching RegEx
                        reason shouldBe "INEFFECTIVE_VULNERABILITY"
                        comment shouldBe "This is ineffective because of some reasons."
                    }
                }
            }
        }

        "show only the unresolved vulnerabilities of an ORT run" {
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
                            "VulnerableCode" to PluginConfig(
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
                    environment = generateAdvisorEnvironment(),
                    config = generateAdvisorConfiguration(),
                    results = generateAdvisorResult()
                )

                // Add a resolution for one of the vulnerabilities.
                val vulnerabilityResolution = VulnerabilityResolution(
                    externalId = ".*CVE-2021-12.*", // Matching RegEx
                    reason = "INEFFECTIVE_VULNERABILITY",
                    comment = "This is ineffective because of some reasons."
                )
                dbExtension.fixtures.resolvedConfigurationRepository.addResolutions(
                    ortRun.id,
                    Resolutions(vulnerabilities = listOf(vulnerabilityResolution))
                )

                val response = superuserClient.get("/api/v1/runs/${ortRun.id}/vulnerabilities?resolved=false")

                response shouldHaveStatus HttpStatusCode.OK
                val vulnerabilities = response.body<PagedResponse<VulnerabilityWithIdentifier>>()

                with(vulnerabilities.data) {
                    shouldHaveSize(1)
                    first().vulnerability.externalId shouldBe "CVE-2018-14721"
                    first().rating shouldBe VulnerabilityRating.MEDIUM

                    with(first().identifier) {
                        type shouldBe "Maven"
                        namespace shouldBe "com.fasterxml.jackson.core"
                        name shouldBe "jackson-databind"
                        version shouldBe "2.9.6"
                    }

                    first().resolutions shouldBe emptyList()
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
                traceId = "test-trace-id",
                null
            )

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(repositoryId)) {
                get("/api/v1/runs/${run.id}/vulnerabilities")
            }
        }
    }

    "GET /runs/{runId}/issues" should {
        "require RepositoryPermission.READ_ORT_RUNS" {
            val ortRun = dbExtension.fixtures.createOrtRun(
                repositoryId = repositoryId,
                revision = "revision",
                jobConfigurations = JobConfigurations()
            )

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(repositoryId)) {
                get("/api/v1/runs/${ortRun.id}/issues")
            }
        }

        "handle a non-existing ORT run" {
            integrationTestApplication {
                val response = superuserClient.get("/api/v1/runs/12345/issues")

                response shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "return and empty list of issues if no issues exist" {
            integrationTestApplication {
                val ortRun = dbExtension.fixtures.createOrtRun(
                    repositoryId = repositoryId,
                    revision = "revision",
                    jobConfigurations = JobConfigurations()
                )

                val response = superuserClient.get("/api/v1/runs/${ortRun.id}/issues")

                response shouldHaveStatus HttpStatusCode.OK
                val pagedIssues = response.body<PagedResponse<ApiIssue>>()

                pagedIssues.pagination.totalCount shouldBe 0
                pagedIssues.data shouldHaveSize 0

                // Applies a default sort order
                pagedIssues.pagination.sortProperties.firstOrNull()?.name shouldBe "timestamp"
                pagedIssues.pagination.sortProperties.firstOrNull()?.direction shouldBe SortDirection.DESCENDING
            }
        }

        "return a paginated list of issues including analyzer issues" {
            integrationTestApplication {
                val ortRun = dbExtension.fixtures.createOrtRun(
                    repositoryId = repositoryId,
                    revision = "revision",
                    jobConfigurations = JobConfigurations()
                )

                val analyzerJob = dbExtension.fixtures.createAnalyzerJob(ortRun.id)

                val now = Clock.System.now()
                val identifier = Identifier("Maven", "namespace", "name", "1.0.0")
                dbExtension.fixtures.analyzerRunRepository.create(
                    analyzerJobId = analyzerJob.id,
                    startTime = now.toDatabasePrecision(),
                    endTime = now.toDatabasePrecision(),
                    environment = Environment(
                        ortVersion = "1.0",
                        javaVersion = "11.0.16",
                        os = "Linux",
                        processors = 8,
                        maxMemory = 8321499136,
                        variables = emptyMap(),
                        toolVersions = emptyMap()
                    ),
                    config = AnalyzerConfiguration(
                        allowDynamicVersions = true,
                        enabledPackageManagers = emptyList(),
                        disabledPackageManagers = emptyList(),
                        packageManagers = emptyMap(),
                        skipExcluded = true
                    ),
                    projects = emptySet(),
                    packages = emptySet(),
                    issues = listOf(
                        Issue(
                            timestamp = now.minus(1.hours).toDatabasePrecision(),
                            source = "Maven",
                            message = "Issue 1",
                            severity = Severity.ERROR,
                            affectedPath = "path",
                            identifier = identifier
                        ),
                        Issue(
                            timestamp = now.toDatabasePrecision(),
                            source = "Maven",
                            message = "Issue 2",
                            severity = Severity.WARNING,
                            affectedPath = "path",
                            identifier = identifier
                        ),
                        Issue(
                            timestamp = now.plus(5.seconds).toDatabasePrecision(),
                            source = "Maven",
                            message = "Issue 3",
                            severity = Severity.HINT,
                            affectedPath = "path"
                        )
                    ),
                    dependencyGraphs = emptyMap()
                )

                val response = superuserClient.get("/api/v1/runs/${ortRun.id}/issues?limit=2")

                response shouldHaveStatus HttpStatusCode.OK
                val pagedIssues = response.body<PagedResponse<ApiIssue>>()

                with(pagedIssues.pagination) {
                    totalCount shouldBe 3
                    offset shouldBe 0
                    limit shouldBe 2

                    // Default sort order applied?
                    sortProperties.firstOrNull()?.name shouldBe "timestamp"
                    sortProperties.firstOrNull()?.direction shouldBe SortDirection.DESCENDING
                }

                with(pagedIssues.data) {
                    shouldHaveSize(2)
                    with(get(1)) {
                        now.epochSeconds - timestamp.epochSeconds shouldBeLessThan 10
                        source shouldBe "Maven"
                        message shouldBe "Issue 2"
                        severity shouldBe ApiSeverity.WARNING
                        affectedPath shouldBe "path"
                        worker shouldBe "analyzer"

                        with(identifier) {
                            this.type shouldBe "Maven"
                            this.namespace shouldBe "namespace"
                            this.name shouldBe "name"
                            this.version shouldBe "1.0.0"
                        }
                    }
                    first().identifier should beNull()
                }
            }
        }

        "return a paginated list of issues including advisor issues" {
            integrationTestApplication {
                val ortRun = dbExtension.fixtures.createOrtRun(
                    repositoryId = repositoryId,
                    revision = "revision",
                    jobConfigurations = JobConfigurations()
                )

                val advisorJob = dbExtension.fixtures.createAdvisorJob(ortRun.id)

                val now = Clock.System.now()
                dbExtension.fixtures.advisorRunRepository.create(
                    advisorJobId = advisorJob.id,
                    startTime = now.toDatabasePrecision(),
                    endTime = now.toDatabasePrecision(),
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
                            "VulnerableCode" to PluginConfig(
                                options = mapOf("serverUrl" to "https://public.vulnerablecode.io"),
                                secrets = mapOf("apiKey" to "key")
                            )
                        )
                    ),
                    results = mapOf(
                        Identifier("Maven", "namespace", "name", "1.0.0") to listOf(
                            AdvisorResult(
                                advisorName = "Advisor",
                                capabilities = listOf("VULNERABILITIES"),
                                startTime = now.toDatabasePrecision(),
                                endTime = now.toDatabasePrecision(),
                                issues = listOf(
                                    Issue(
                                        timestamp = now.minus(1.hours).toDatabasePrecision(),
                                        source = "Advisor",
                                        message = "Issue 1",
                                        severity = Severity.ERROR,
                                        affectedPath = "path"
                                    ),
                                    Issue(
                                        timestamp = now.toDatabasePrecision(),
                                        source = "Advisor",
                                        message = "Issue 2",
                                        severity = Severity.WARNING,
                                        affectedPath = "path"
                                    )
                                ),
                                defects = emptyList(),
                                vulnerabilities = emptyList()
                            )
                        )
                    )
                )

                val response = superuserClient.get("/api/v1/runs/${ortRun.id}/issues?limit=1")

                response shouldHaveStatus HttpStatusCode.OK
                val pagedIssues = response.body<PagedResponse<ApiIssue>>()

                with(pagedIssues.pagination) {
                    totalCount shouldBe 2
                    offset shouldBe 0
                    limit shouldBe 1

                    // Default sort order applied?
                    sortProperties.firstOrNull()?.name shouldBe "timestamp"
                    sortProperties.firstOrNull()?.direction shouldBe SortDirection.DESCENDING
                }

                with(pagedIssues.data) {
                    shouldHaveSize(1)
                    with(first()) {
                        timestamp.epochSeconds shouldBe now.epochSeconds
                        source shouldBe "Advisor"
                        message shouldBe "Issue 2"
                        severity shouldBe ApiSeverity.WARNING
                        affectedPath shouldBe "path"
                        worker shouldBe "advisor"

                        with(identifier) {
                            this?.type shouldBe "Maven"
                            this?.namespace shouldBe "namespace"
                            this?.name shouldBe "name"
                            this?.version shouldBe "1.0.0"
                        }
                    }
                }
            }
        }
    }

    "GET /runs/{runId}/packages" should {
        "show the packages found in an ORT run" {
            integrationTestApplication {
                val ortRun = dbExtension.fixtures.createOrtRun(
                    repositoryId = repositoryId,
                    revision = "revision",
                    jobConfigurations = JobConfigurations()
                )

                val analyzerJob = dbExtension.fixtures.createAnalyzerJob(
                    ortRunId = ortRun.id,
                    configuration = AnalyzerJobConfiguration()
                )

                val project = dbExtension.fixtures.getProject()
                val identifier1 = Identifier("Maven", "com.example", "example", "1.0")
                val identifier2 = Identifier("Maven", "com.example", "example2", "1.0")

                dbExtension.fixtures.analyzerRunRepository.create(
                    analyzerJobId = analyzerJob.id,
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
                    config = AnalyzerConfiguration(
                        allowDynamicVersions = true,
                        enabledPackageManagers = emptyList(),
                        disabledPackageManagers = emptyList(),
                        packageManagers = emptyMap(),
                        skipExcluded = true
                    ),
                    projects = setOf(project),
                    packages = setOf(
                        Package(
                            identifier = identifier1,
                            purl = "pkg:maven/com.example/example@1.0",
                            cpe = null,
                            authors = setOf("Author One", "Author Two"),
                            declaredLicenses = setOf("License1", "License2", "License3"),
                            processedDeclaredLicense = ProcessedDeclaredLicense(
                                spdxExpression = "Expression",
                                mappedLicenses = mapOf(
                                    "License 1" to "LicenseRef-mapped-1",
                                    "License 2" to "LicenseRef-mapped-2",
                                ),
                                unmappedLicenses = setOf("License 1", "License 2", "License 3", "License 4")
                            ),
                            description = "An example package",
                            homepageUrl = "https://example.com",
                            binaryArtifact = RemoteArtifact(
                                "https://example.com/example-1.0.jar",
                                "0123456789abcdef0123456789abcdef01234567",
                                "SHA-1"
                            ),
                            sourceArtifact = RemoteArtifact(
                                "https://example.com/example-1.0-sources.jar",
                                "0123456789abcdef0123456789abcdef01234567",
                                "SHA-1"
                            ),
                            vcs = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            vcsProcessed = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            isMetadataOnly = false,
                            isModified = false
                        ),
                        Package(
                            identifier = identifier2,
                            purl = "pkg:maven/com.example/example2@1.0",
                            cpe = null,
                            authors = emptySet(),
                            declaredLicenses = emptySet(),
                            processedDeclaredLicense = ProcessedDeclaredLicense(
                                spdxExpression = "Expression",
                                mappedLicenses = emptyMap(),
                                unmappedLicenses = emptySet()
                            ),
                            description = "Another example package",
                            homepageUrl = "https://example.com",
                            binaryArtifact = RemoteArtifact(
                                "https://example.com/example2-1.0.jar",
                                "0123456789abcdef0123456789abcdef01234567",
                                "SHA-1"
                            ),
                            sourceArtifact = RemoteArtifact(
                                "https://example.com/example2-1.0-sources.jar",
                                "0123456789abcdef0123456789abcdef01234567",
                                "SHA-1"
                            ),
                            vcs = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            vcsProcessed = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            isMetadataOnly = false,
                            isModified = false
                        )
                    ),
                    issues = emptyList(),
                    dependencyGraphs = emptyMap(),
                    shortestDependencyPaths = mapOf(
                        identifier1 to listOf(
                            ShortestDependencyPath(
                                project.identifier,
                                "compileClassPath",
                                emptyList()
                            )
                        ),
                        identifier2 to listOf(
                            ShortestDependencyPath(
                                project.identifier,
                                "compileClassPath",
                                listOf(identifier1)
                            )
                        )
                    )
                )

                val response = superuserClient.get("/api/v1/runs/${ortRun.id}/packages")

                response shouldHaveStatus HttpStatusCode.OK
                val packages = response.body<PagedSearchResponse<ApiPackage, PackageFilters>>()

                with(packages.data) {
                    shouldHaveSize(2)

                    with(first()) {
                        identifier.name shouldBe "example"
                        authors shouldHaveSize 2
                        declaredLicenses shouldHaveSize 3
                        processedDeclaredLicense.mappedLicenses shouldHaveSize 2
                        processedDeclaredLicense.unmappedLicenses shouldHaveSize 4

                        shortestDependencyPaths.shouldBeSingleton {
                            it.projectIdentifier shouldBe project.identifier.mapToApi()
                            it.scope shouldBe "compileClassPath"
                            it.path shouldBe emptyList()
                        }
                    }

                    last().shortestDependencyPaths.shouldBeSingleton {
                        it.projectIdentifier shouldBe project.identifier.mapToApi()
                        it.scope shouldBe "compileClassPath"
                        it.path shouldBe listOf(identifier1.mapToApi())
                    }
                    last().identifier.name shouldBe "example2"
                }
            }
        }

        "allow filtering by processed declared license and identifier" {
            integrationTestApplication {
                val ortRunId = dbExtension.fixtures.createOrtRun(
                    repositoryId = repositoryId,
                    revision = "revision",
                    jobConfigurations = JobConfigurations()
                ).id

                val analyzerJobId = dbExtension.fixtures.createAnalyzerJob(
                    ortRunId = ortRunId,
                    configuration = AnalyzerJobConfiguration()
                ).id

                val identifier1 = Identifier("Maven", "com.example", "example", "1.0")
                val identifier2 = Identifier("Maven", "com.example", "example2", "1.0")
                val identifier3 = Identifier("Maven", "com.example", "example3", "1.0")
                val identifier4 = Identifier("NPM", "com.example", "example4", "1.0")

                dbExtension.fixtures.createAnalyzerRun(
                    analyzerJobId,
                    packages = setOf(
                        dbExtension.fixtures.generatePackage(
                            identifier1,
                            processedDeclaredLicense = ProcessedDeclaredLicense(
                                "Apache-2.0 OR LGPL-2.1-or-later",
                                emptyMap(),
                                emptySet()
                            )
                        ),
                        dbExtension.fixtures.generatePackage(
                            identifier2,
                            processedDeclaredLicense = ProcessedDeclaredLicense(
                                "Apache-2.0",
                                emptyMap(),
                                emptySet()
                            )
                        ),
                        dbExtension.fixtures.generatePackage(
                            identifier3,
                            processedDeclaredLicense = ProcessedDeclaredLicense(
                                "Apache-2.0",
                                emptyMap(),
                                emptySet()
                            )
                        ),
                        dbExtension.fixtures.generatePackage(
                            identifier4,
                            processedDeclaredLicense = ProcessedDeclaredLicense(
                                "MIT",
                                emptyMap(),
                                emptySet()
                            )
                        )
                    )
                )

                val response = superuserClient.get("/api/v1/runs/$ortRunId/packages") {
                    url {
                        parameters.append("processedDeclaredLicense", "-,Apache-2.0 OR LGPL-2.1-or-later,MIT")
                        parameters.append("identifier", "example3")
                    }
                }

                response shouldHaveStatus HttpStatusCode.OK
                val packages = response.body<PagedSearchResponse<ApiPackage, PackageFilters>>()

                packages.data.size shouldBe 1
                packages.data.first().processedDeclaredLicense.spdxExpression shouldBe "Apache-2.0"
                packages.data.first().identifier shouldBe identifier3.mapToApi()

                packages.filters shouldBe PackageFilters(
                    processedDeclaredLicense = FilterOperatorAndValue(
                        ComparisonOperator.NOT_IN,
                        setOf("Apache-2.0 OR LGPL-2.1-or-later", "MIT")
                    ),
                    identifier = FilterOperatorAndValue(ComparisonOperator.ILIKE, "example3")
                )
            }
        }

        "allow filtering by purl" {
            integrationTestApplication {
                val ortRunId = dbExtension.fixtures.createOrtRun(
                    repositoryId = repositoryId,
                    revision = "revision",
                    jobConfigurations = JobConfigurations()
                ).id

                val analyzerJobId = dbExtension.fixtures.createAnalyzerJob(
                    ortRunId = ortRunId,
                    configuration = AnalyzerJobConfiguration()
                ).id

                val identifier1 = Identifier("Maven", "com.example", "example", "1.0")
                val identifier2 = Identifier("Maven", "com.example", "example2", "1.0")
                val identifier3 = Identifier("NPM", "com.example", "example3", "1.0")

                dbExtension.fixtures.createAnalyzerRun(
                    analyzerJobId,
                    packages = setOf(
                        dbExtension.fixtures.generatePackage(identifier1),
                        dbExtension.fixtures.generatePackage(identifier2),
                        dbExtension.fixtures.generatePackage(identifier3)
                    )
                )

                val response = superuserClient.get("/api/v1/runs/$ortRunId/packages") {
                    url {
                        parameters.append("purl", "pkg:Maven/com.example/example")
                    }
                }

                response shouldHaveStatus HttpStatusCode.OK
                val packages = response.body<PagedSearchResponse<ApiPackage, PackageFilters>>()

                packages.data.size shouldBe 2

                packages.data.first().purl shouldBe "pkg:Maven/com.example/example@1.0"
                packages.data.last().purl shouldBe "pkg:Maven/com.example/example2@1.0"

                packages.filters shouldBe PackageFilters(
                    purl = FilterOperatorAndValue(
                        ComparisonOperator.ILIKE, "pkg:Maven/com.example/example"
                    )
                )
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
                traceId = "test-trace-id",
                null
            )

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(repositoryId)) {
                get("/api/v1/runs/${run.id}/packages")
            }
        }
    }

    "GET /runs/{runId}/projects" should {
        "show the projects found in an ORT run" {
            integrationTestApplication {
                val ortRun = dbExtension.fixtures.createOrtRun(
                    repositoryId = repositoryId,
                    revision = "revision",
                    jobConfigurations = JobConfigurations()
                )

                val analyzerJob = dbExtension.fixtures.createAnalyzerJob(
                    ortRunId = ortRun.id,
                    configuration = AnalyzerJobConfiguration()
                )

                dbExtension.fixtures.analyzerRunRepository.create(
                    analyzerJobId = analyzerJob.id,
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
                    config = AnalyzerConfiguration(
                        allowDynamicVersions = true,
                        enabledPackageManagers = emptyList(),
                        disabledPackageManagers = emptyList(),
                        packageManagers = emptyMap(),
                        skipExcluded = true
                    ),
                    projects = setOf(
                        Project(
                            Identifier("Maven", "com.example", "example", "1.0"),
                            null,
                            "pom.xml",
                            setOf("Author One", "Author Two"),
                            declaredLicenses = setOf("License1", "License2", "License3"),
                            ProcessedDeclaredLicense(
                                spdxExpression = "Expression",
                                mappedLicenses = mapOf(
                                    "License 1" to "LicenseRef-mapped-1",
                                    "License 2" to "LicenseRef-mapped-2",
                                ),
                                unmappedLicenses = setOf("License 1", "License 2", "License 3", "License 4")
                            ),
                            VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            "ExampleDescription",
                            "https://example.com",
                            emptySet(),
                        )
                    ),
                    packages = emptySet(),
                    issues = emptyList(),
                    dependencyGraphs = emptyMap()
                )

                val response = superuserClient.get("/api/v1/runs/${ortRun.id}/projects")

                response shouldHaveStatus HttpStatusCode.OK
                val projects = response.body<PagedResponse<ApiProject>>()

                with(projects.data) {
                    shouldHaveSize(1)
                    first().identifier.name shouldBe "example"
                    first().authors shouldHaveSize 2
                    first().declaredLicenses shouldHaveSize 3
                    first().processedDeclaredLicense.mappedLicenses shouldHaveSize 2
                    first().processedDeclaredLicense.unmappedLicenses shouldHaveSize 4
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
                traceId = "test-trace-id",
                null
            )

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(repositoryId)) {
                get("/api/v1/runs/${run.id}/projects")
            }
        }
    }

    "GET /runs" should {
        "return a paginated list of ORT runs from all organizations" {
            integrationTestApplication {
                val orgId2 = organizationService.createOrganization(name = "name2", description = "description").id
                val productId2 =
                    organizationService.createProduct(
                        name = "name",
                        description = "description",
                        organizationId = orgId2
                    ).id
                val repositoryId2 = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example2.org/repo.git",
                    productId = productId2,
                    description = "description"
                ).id

                val run1 = ortRunRepository.create(
                    repositoryId,
                    "revision",
                    null,
                    JobConfigurations(),
                    "jobConfigContext",
                    labelsMap,
                    traceId = "t1",
                    null
                )

                val run2 = ortRunRepository.create(
                    repositoryId2,
                    "revision",
                    null,
                    JobConfigurations(),
                    "jobConfigContext",
                    labelsMap,
                    traceId = "t2",
                    null
                )

                val response = superuserClient.get("/api/v1/runs")

                response shouldHaveStatus HttpStatusCode.OK

                val body = response.body<PagedSearchResponse<OrtRunSummary, OrtRunFilters>>()

                with(body.data) {
                    shouldHaveSize(2)
                    first() shouldBe run2.mapToApiSummary(JobSummaries())
                    last() shouldBe run1.mapToApiSummary(JobSummaries())
                }

                body.pagination.sortProperties shouldBe listOf(SortProperty("createdAt", SortDirection.DESCENDING))
                body.filters shouldBe OrtRunFilters()
            }
        }

        "return a sorted and filtered list of ORT runs" {
            integrationTestApplication {
                ortRunRepository.create(
                    repositoryId,
                    "revision1",
                    null,
                    JobConfigurations(),
                    "jobConfigContext",
                    labelsMap,
                    traceId = "t1",
                    null
                )

                val run2 = ortRunRepository.create(
                    repositoryId,
                    "revision2",
                    null,
                    JobConfigurations(),
                    "jobConfigContext",
                    labelsMap,
                    traceId = "t2",
                    null
                )

                val run3 = ortRunRepository.create(
                    repositoryId,
                    "revision3",
                    null,
                    JobConfigurations(),
                    "jobConfigContext",
                    labelsMap,
                    traceId = "t3",
                    null
                )

                val updatedRun2 = ortRunRepository.update(run2.id, OrtRunStatus.FAILED.asPresent())
                val updatedRun3 = ortRunRepository.update(run3.id, OrtRunStatus.FINISHED_WITH_ISSUES.asPresent())

                val response = superuserClient.get("/api/v1/runs?status=failed,finished_with_issues&sort=revision")

                response shouldHaveStatus HttpStatusCode.OK

                val body = response.body<PagedSearchResponse<OrtRunSummary, OrtRunFilters>>()

                with(body.data) {
                    shouldHaveSize(2)
                    first() shouldBe updatedRun2.mapToApiSummary(JobSummaries())
                    last() shouldBe updatedRun3.mapToApiSummary(JobSummaries())
                }

                with(body.pagination) {
                    sortProperties shouldBe listOf(SortProperty("revision", SortDirection.ASCENDING))
                    totalCount shouldBe 2
                }

                body.filters shouldBe OrtRunFilters(
                    status = FilterOperatorAndValue(
                        ComparisonOperator.IN,
                        setOf(ApiOrtRunStatus.FAILED, ApiOrtRunStatus.FINISHED_WITH_ISSUES)
                    )
                )
            }
        }

        "exclude specified statuses if requested" {
            integrationTestApplication {
                val run1 = ortRunRepository.create(
                    repositoryId,
                    "revision1",
                    null,
                    JobConfigurations(),
                    "jobConfigContext",
                    labelsMap,
                    traceId = "t1",
                    ".custom.ort.env.yml"
                )

                val run2 = ortRunRepository.create(
                    repositoryId,
                    "revision2",
                    null,
                    JobConfigurations(),
                    "jobConfigContext",
                    labelsMap,
                    traceId = "t2",
                    null
                )

                val run3 = ortRunRepository.create(
                    repositoryId,
                    "revision3",
                    null,
                    JobConfigurations(),
                    "jobConfigContext",
                    labelsMap,
                    traceId = "t3",
                    null
                )

                ortRunRepository.update(run2.id, OrtRunStatus.FAILED.asPresent())
                ortRunRepository.update(run3.id, OrtRunStatus.FINISHED_WITH_ISSUES.asPresent())

                val response = superuserClient.get("/api/v1/runs?status=-,failed,finished_with_issues&sort=revision")

                response shouldHaveStatus HttpStatusCode.OK

                val body = response.body<PagedSearchResponse<OrtRunSummary, OrtRunFilters>>()

                with(body.data) {
                    shouldHaveSize(1)
                    first() shouldBe run1.mapToApiSummary(JobSummaries())
                }

                with(body.pagination) {
                    sortProperties shouldBe listOf(SortProperty("revision", SortDirection.ASCENDING))
                    totalCount shouldBe 1
                }

                body.filters shouldBe OrtRunFilters(
                    status = FilterOperatorAndValue(
                        ComparisonOperator.NOT_IN,
                        setOf(ApiOrtRunStatus.FAILED, ApiOrtRunStatus.FINISHED_WITH_ISSUES)
                    )
                )
            }
        }

        "require superuser role" {
            requestShouldRequireRole(Superuser.ROLE_NAME) {
                get("/api/v1/runs")
            }
        }
    }

    "GET /runs/{runId}/rule-violations" should {
        "require RepositoryPermission.READ_ORT_RUNS" {
            val ortRun = dbExtension.fixtures.createOrtRun(
                repositoryId = repositoryId,
                revision = "revision",
                jobConfigurations = JobConfigurations()
            )

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(repositoryId)) {
                get("/api/v1/runs/${ortRun.id}/rule-violations")
            }
        }

        "return a paginated and properly sorted list of rule violations for given runId" {
            integrationTestApplication {
                val ortRun = dbExtension.fixtures.createOrtRun(
                    repositoryId = repositoryId,
                    revision = "revision",
                    jobConfigurations = JobConfigurations()
                )

                val evaluatorJob = dbExtension.fixtures.createEvaluatorJob(
                    ortRunId = ortRun.id,
                    configuration = EvaluatorJobConfiguration()
                )

                val ruleViolations = listOf(
                    // 4th record after sort by "rule"
                    OrtRuleViolation(
                        "z-Rule-1",
                        Identifier(
                            "Maven",
                            "org.apache.logging.log4j",
                            "log4j-core",
                            "2.14.0"
                        ),
                        "License-1",
                        "CONCLUDED",
                        Severity.WARNING,
                        "Message-1",
                        "How_to_fix-1"
                    ),
                    // 3rd record after sort by "rule"
                    OrtRuleViolation(
                        "b-Rule-2",
                        Identifier(
                            "Maven",
                            "com.fasterxml.jackson.core",
                            "jackson-databind",
                            "2.9.6"
                        ),
                        "License-2",
                        "DETECTED",
                        Severity.ERROR,
                        "Message-2",
                        "How_to_fix-2"
                    ),
                    // 1st record after sort by "rule"
                    OrtRuleViolation(
                        "1-Rule-3",
                        Identifier(
                            "Maven",
                            "org.apache.logging.log4j.api",
                            "log4j-core-api",
                            "2.20.0"
                        ),
                        "License-3",
                        "CONCLUDED",
                        Severity.WARNING,
                        "Message-3",
                        "How_to_fix-3"
                    ),
                    // 2nd record after sort by "rule", without package identifier
                    OrtRuleViolation(
                        "a-Rule-4",
                        null,
                        "License-4",
                        "CONCLUDED",
                        Severity.WARNING,
                        "Message-4",
                        "How_to_fix-4"
                    )
                )

                ruleViolations.forEach {
                    it.packageId?.let { identifier ->
                        dbExtension.fixtures.createIdentifier(
                            identifier
                        )
                    }
                }

                dbExtension.fixtures.evaluatorRunRepository.create(
                    evaluatorJobId = evaluatorJob.id,
                    startTime = Clock.System.now().toDatabasePrecision(),
                    endTime = Clock.System.now().toDatabasePrecision(),
                    violations = ruleViolations
                )

                // 2nd ortRun just to check if filtering is working OK
                val obsoleteOrtRun = dbExtension.fixtures.createOrtRun(
                    repositoryId = repositoryId,
                    revision = "revision",
                    jobConfigurations = JobConfigurations()
                )

                val obsoleteEvaluatorJob = dbExtension.fixtures.createEvaluatorJob(
                    ortRunId = obsoleteOrtRun.id,
                    configuration = EvaluatorJobConfiguration()
                )

                val obsoleteRuleViolations = listOf(
                    OrtRuleViolation(
                        "Rule-1-obsolete",
                        Identifier(
                            "Maven",
                            "org.apache.logging.log4j.obsolete",
                            "log4j-core",
                            "2.14.0"
                        ),
                        "License-1-obsolete",
                        "CONCLUDED",
                        Severity.WARNING,
                        "Message-1-obsolete",
                        "How_to_fix-1-obsolete"
                    ),
                    OrtRuleViolation(
                        "Rule-2-obsolete",
                        null,
                        "License-2-obsolete",
                        "DETECTED",
                        Severity.ERROR,
                        "Message-2-obsolete",
                        "How_to_fix-2-obsolete"
                    )
                )

                obsoleteRuleViolations.forEach {
                    it.packageId?.let { identifier ->
                        dbExtension.fixtures.createIdentifier(
                            identifier
                        )
                    }
                }

                dbExtension.fixtures.evaluatorRunRepository.create(
                    evaluatorJobId = obsoleteEvaluatorJob.id,
                    startTime = Clock.System.now().toDatabasePrecision(),
                    endTime = Clock.System.now().toDatabasePrecision(),
                    violations = obsoleteRuleViolations
                )

                val response = superuserClient.get("/api/v1/runs/${ortRun.id}/rule-violations")

                response shouldHaveStatus HttpStatusCode.OK

                val ruleViolationsResponse = response.body<PagedResponse<RuleViolation>>()

                ruleViolationsResponse.data shouldHaveSize(4)

                with(ruleViolationsResponse.pagination) {
                    sortProperties shouldBe listOf(SortProperty("rule", SortDirection.ASCENDING))
                    totalCount shouldBe 4
                }

                ruleViolationsResponse.data[0] shouldBe ruleViolations[2].mapToApi()
                ruleViolationsResponse.data[1] shouldBe ruleViolations[3].mapToApi()
                ruleViolationsResponse.data[2] shouldBe ruleViolations[1].mapToApi()
                ruleViolationsResponse.data[3] shouldBe ruleViolations[0].mapToApi()
            }
        }
    }

    "GET /runs/{runId}/statistics" should {
        "return statistics counts for the run" {
            integrationTestApplication {
                val ortRun = dbExtension.fixtures.createOrtRun(
                    repositoryId = repositoryId,
                    revision = "revision",
                    jobConfigurations = JobConfigurations()
                )

                val now = Clock.System.now()

                val analyzerJob = dbExtension.fixtures.createAnalyzerJob(
                    ortRunId = ortRun.id,
                    configuration = AnalyzerJobConfiguration()
                )

                dbExtension.fixtures.analyzerRunRepository.create(
                    analyzerJobId = analyzerJob.id,
                    startTime = now.toDatabasePrecision(),
                    endTime = now.toDatabasePrecision(),
                    environment = Environment(
                        ortVersion = "1.0",
                        javaVersion = "11.0.16",
                        os = "Linux",
                        processors = 8,
                        maxMemory = 8321499136,
                        variables = emptyMap(),
                        toolVersions = emptyMap()
                    ),
                    config = AnalyzerConfiguration(
                        allowDynamicVersions = true,
                        enabledPackageManagers = emptyList(),
                        disabledPackageManagers = emptyList(),
                        packageManagers = emptyMap(),
                        skipExcluded = true
                    ),
                    projects = emptySet(),
                    packages = setOf(
                        Package(
                            identifier = Identifier("Maven", "com.example", "example", "1.0"),
                            purl = "pkg:maven/com.example/example@1.0",
                            cpe = null,
                            authors = emptySet(),
                            declaredLicenses = emptySet(),
                            processedDeclaredLicense = ProcessedDeclaredLicense(
                                spdxExpression = null,
                                mappedLicenses = emptyMap(),
                                unmappedLicenses = emptySet(),
                            ),
                            description = "An example package",
                            homepageUrl = "https://example.com",
                            binaryArtifact = RemoteArtifact(
                                "https://example.com/example-1.0.jar",
                                "0123456789abcdef0123456789abcdef01234567",
                                "SHA-1"
                            ),
                            sourceArtifact = RemoteArtifact(
                                "https://example.com/example-1.0-sources.jar",
                                "0123456789abcdef0123456789abcdef01234567",
                                "SHA-1"
                            ),
                            vcs = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            vcsProcessed = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            isMetadataOnly = false,
                            isModified = false
                        ),
                        Package(
                            identifier = Identifier("NPM", "com.example", "example2", "1.0"),
                            purl = "pkg:npm/com.example/example2@1.0",
                            cpe = null,
                            authors = emptySet(),
                            declaredLicenses = emptySet(),
                            processedDeclaredLicense = ProcessedDeclaredLicense(
                                spdxExpression = null,
                                mappedLicenses = emptyMap(),
                                unmappedLicenses = emptySet()
                            ),
                            description = "Another example package",
                            homepageUrl = "https://example.com",
                            binaryArtifact = RemoteArtifact(
                                "https://example.com/example2-1.0.jar",
                                "0123456789abcdef0123456789abcdef01234567",
                                "SHA-1"
                            ),
                            sourceArtifact = RemoteArtifact(
                                "https://example.com/example2-1.0-sources.jar",
                                "0123456789abcdef0123456789abcdef01234567",
                                "SHA-1"
                            ),
                            vcs = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            vcsProcessed = VcsInfo(
                                RepositoryType("GIT"),
                                "https://example.com/git",
                                "revision",
                                "path"
                            ),
                            isMetadataOnly = false,
                            isModified = false
                        )
                    ),
                    issues = listOf(
                        Issue(
                            timestamp = now.minus(1.hours).toDatabasePrecision(),
                            source = "Maven",
                            message = "Issue 1",
                            severity = Severity.ERROR,
                            affectedPath = "path",
                            identifier = Identifier("Maven", "com.example", "example", "1.0"),
                        ),
                    ),
                    dependencyGraphs = emptyMap()
                )

                dbExtension.fixtures.analyzerJobRepository.update(
                    analyzerJob.id,
                    finishedAt = Clock.System.now().asPresent(),
                    status = JobStatus.FINISHED_WITH_ISSUES.asPresent()
                )

                val advisorJob = dbExtension.fixtures.createAdvisorJob(ortRun.id)

                dbExtension.fixtures.advisorRunRepository.create(
                    advisorJobId = advisorJob.id,
                    startTime = now.toDatabasePrecision(),
                    endTime = now.toDatabasePrecision(),
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
                            "VulnerableCode" to PluginConfig(
                                options = mapOf("serverUrl" to "https://public.vulnerablecode.io"),
                                secrets = mapOf("apiKey" to "key")
                            )
                        )
                    ),
                    results = mapOf(
                        Identifier("NPM", "com.example", "example2", "1.0") to listOf(
                            AdvisorResult(
                                advisorName = "Advisor",
                                capabilities = listOf("vulnerabilities"),
                                startTime = now.toDatabasePrecision(),
                                endTime = now.toDatabasePrecision(),
                                issues = listOf(
                                    Issue(
                                        timestamp = now.minus(1.hours).toDatabasePrecision(),
                                        source = "Advisor",
                                        message = "Issue 1",
                                        severity = Severity.ERROR,
                                        affectedPath = "path"
                                    ),
                                    Issue(
                                        timestamp = now.toDatabasePrecision(),
                                        source = "Advisor",
                                        message = "Issue 2",
                                        severity = Severity.WARNING,
                                        affectedPath = "path"
                                    )
                                ),
                                defects = emptyList(),
                                vulnerabilities = listOf(
                                    Vulnerability(
                                        externalId = "CVE-2021-1234",
                                        summary = "A vulnerability",
                                        description = "A description",
                                        references = listOf(
                                            VulnerabilityReference(
                                                url = "https://example.com",
                                                scoringSystem = "CVSS",
                                                severity = "MEDIUM",
                                                score = 5.1f,
                                                vector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )

                dbExtension.fixtures.advisorJobRepository.update(
                    advisorJob.id,
                    finishedAt = Clock.System.now().asPresent(),
                    status = JobStatus.FINISHED_WITH_ISSUES.asPresent()
                )

                val evaluatorJob = dbExtension.fixtures.createEvaluatorJob(
                    ortRunId = ortRun.id,
                    configuration = EvaluatorJobConfiguration()
                )

                dbExtension.fixtures.evaluatorRunRepository.create(
                    evaluatorJobId = evaluatorJob.id,
                    startTime = now.toDatabasePrecision(),
                    endTime = now.toDatabasePrecision(),
                    violations = listOf(
                        // 4th record after sort by "rule"
                        OrtRuleViolation(
                            "z-Rule-1",
                            Identifier("Maven", "com.example", "example", "1.0"),
                            "License-1",
                            "CONCLUDED",
                            Severity.WARNING,
                            "Message-1",
                            "How_to_fix-1"
                        ),
                        // 3rd record after sort by "rule"
                        OrtRuleViolation(
                            "b-Rule-2",
                            Identifier("Maven", "com.example", "example", "1.0"),
                            "License-2",
                            "DETECTED",
                            Severity.ERROR,
                            "Message-2",
                            "How_to_fix-2"
                        ),
                        // 1st record after sort by "rule"
                        OrtRuleViolation(
                            "1-Rule-3",
                            Identifier("NPM", "com.example", "example2", "1.0"),
                            "License-3",
                            "CONCLUDED",
                            Severity.WARNING,
                            "Message-3",
                            "How_to_fix-3"
                        ),
                        // 2nd record after sort by "rule", without package identifier
                        OrtRuleViolation(
                            "a-Rule-4",
                            null,
                            "License-4",
                            "CONCLUDED",
                            Severity.WARNING,
                            "Message-4",
                            "How_to_fix-4"
                        )
                    )
                )

                dbExtension.fixtures.evaluatorJobRepository.update(
                    evaluatorJob.id,
                    finishedAt = Clock.System.now().asPresent(),
                    status = JobStatus.FINISHED_WITH_ISSUES.asPresent()
                )

                val response = superuserClient.get("/api/v1/runs/${ortRun.id}/statistics")

                response shouldHaveStatus HttpStatusCode.OK

                val statistics = response.body<OrtRunStatistics>()

                with(statistics) {
                    issuesCount shouldBe 3
                    issuesCountBySeverity?.shouldContainExactly(
                        mapOf(
                            ApiSeverity.HINT to 0,
                            ApiSeverity.WARNING to 1,
                            ApiSeverity.ERROR to 2
                        )
                    )
                    packagesCount shouldBe 2
                    ecosystems?.shouldContainExactlyInAnyOrder(
                        listOf(
                            EcosystemStats("NPM", 1),
                            EcosystemStats("Maven", 1)
                        )
                    )
                    vulnerabilitiesCount shouldBe 1
                    vulnerabilitiesCountByRating?.shouldContainExactly(
                        mapOf(
                            VulnerabilityRating.NONE to 0,
                            VulnerabilityRating.LOW to 0,
                            VulnerabilityRating.MEDIUM to 1,
                            VulnerabilityRating.HIGH to 0,
                            VulnerabilityRating.CRITICAL to 0
                        )
                    )
                    ruleViolationsCount shouldBe 4
                    ruleViolationsCountBySeverity?.shouldContainExactly(
                        mapOf(
                            ApiSeverity.HINT to 0,
                            ApiSeverity.WARNING to 3,
                            ApiSeverity.ERROR to 1
                        )
                    )
                }
            }
        }

        "return null for the count if the corresponding job is skipped or has not finished yet" {
            integrationTestApplication {
                val ortRun = dbExtension.fixtures.createOrtRun(
                    repositoryId = repositoryId,
                    revision = "revision",
                    jobConfigurations = JobConfigurations()
                )

                dbExtension.fixtures.createAnalyzerJob(ortRun.id)
                dbExtension.fixtures.createAdvisorJob(ortRun.id)

                val response = superuserClient.get("/api/v1/runs/${ortRun.id}/statistics")

                response shouldHaveStatus HttpStatusCode.OK

                val statistics = response.body<OrtRunStatistics>()

                with(statistics) {
                    issuesCount should beNull()
                    issuesCountBySeverity should beNull()
                    packagesCount should beNull()
                    ecosystems should beNull()
                    vulnerabilitiesCount should beNull()
                    vulnerabilitiesCountByRating should beNull()
                    ruleViolationsCount should beNull()
                    ruleViolationsCountBySeverity should beNull()
                }
            }
        }

        "require RepositoryPermission.READ_ORT_RUNS" {
            val ortRun = dbExtension.fixtures.createOrtRun(
                repositoryId = repositoryId,
                revision = "revision",
                jobConfigurations = JobConfigurations()
            )

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(repositoryId)) {
                get("/api/v1/runs/${ortRun.id}/statistics")
            }
        }
    }

    "GET /runs/{runId}/packages/licenses" should {
        "return the processed declared licenses found in packages" {
            integrationTestApplication {
                val ortRunId = dbExtension.fixtures.createOrtRun(
                    repositoryId = repositoryId,
                    revision = "revision",
                    jobConfigurations = JobConfigurations()
                ).id

                val analyzerJobId = dbExtension.fixtures.createAnalyzerJob(ortRunId).id

                dbExtension.fixtures.createAnalyzerRun(
                    analyzerJobId,
                    packages = setOf(
                        dbExtension.fixtures.generatePackage(
                            Identifier("Maven", "com.example", "example", "1.0"),
                            processedDeclaredLicense = ProcessedDeclaredLicense(
                                "LGPL-2.1-or-later",
                                emptyMap(),
                                emptySet()
                            )
                        ),
                        dbExtension.fixtures.generatePackage(
                            Identifier("Maven", "com.example", "example2", "1.0"),
                            processedDeclaredLicense = ProcessedDeclaredLicense(
                                "Apache-2.0 OR LGPL-2.1-or-later",
                                emptyMap(),
                                emptySet()
                            )
                        ),
                        dbExtension.fixtures.generatePackage(
                            Identifier("NPM", "", "example", "1.0"),
                            processedDeclaredLicense = ProcessedDeclaredLicense(
                                "MIT",
                                emptyMap(),
                                emptySet()
                            )
                        )
                    )
                )

                val response = superuserClient.get("/api/v1/runs/$ortRunId/packages/licenses")

                response shouldHaveStatus HttpStatusCode.OK

                val licenses = response.body<Licenses>()

                licenses.processedDeclaredLicenses shouldBe listOf(
                    "Apache-2.0 OR LGPL-2.1-or-later",
                    "LGPL-2.1-or-later",
                    "MIT"
                )
            }
        }

        "require RepositoryPermission.READ_ORT_RUNS" {
            val ortRunId = dbExtension.fixtures.createOrtRun(
                repositoryId = repositoryId,
                revision = "revision",
                jobConfigurations = JobConfigurations()
            ).id

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(repositoryId)) {
                get("/api/v1/runs/$ortRunId/packages/licenses")
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
    archiveFile shouldBe aFile()
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

private fun generateAdvisorEnvironment() = Environment(
    ortVersion = "1.0",
    javaVersion = "11.0.16",
    os = "Linux",
    processors = 8,
    maxMemory = 8321499136,
    variables = emptyMap(),
    toolVersions = emptyMap()
)

private fun generateAdvisorConfiguration() = AdvisorConfiguration(
    config = mapOf(
        "VulnerableCode" to PluginConfig(
            options = mapOf("serverUrl" to "https://public.vulnerablecode.io"),
            secrets = mapOf("apiKey" to "key")
        )
    )
)

private fun generateAdvisorResult() = mapOf(
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
                        references = listOf(
                            VulnerabilityReference(
                                url = "https://example.com",
                                scoringSystem = "CVSS",
                                severity = "LOW",
                                score = 1.2f,
                                vector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                            ),
                            VulnerabilityReference(
                                url = "https://example.com",
                                scoringSystem = "CVSS",
                                severity = "MEDIUM",
                                score = 4.2f,
                                vector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                            )
                        )
                    )
                )
            )
        ),
        Identifier("Maven", "com.fasterxml.jackson.core", "jackson-databind", "2.9.6") to listOf(
            AdvisorResult(
                advisorName = "advisor",
                capabilities = listOf("vulnerabilities"),
                startTime = Clock.System.now().toDatabasePrecision(),
                endTime = Clock.System.now().toDatabasePrecision(),
                issues = emptyList(),
                defects = emptyList(),
                vulnerabilities = listOf(
                    Vulnerability(
                        externalId = "CVE-2018-14721",
                        summary = "A vulnerability",
                        description = "A description",
                        references = listOf(
                            VulnerabilityReference(
                                url = "https://example.com",
                                scoringSystem = "CVSS",
                                severity = "MEDIUM",
                                score = 4.2f,
                                vector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                            )
                        )
                    )
                )
            )
        )
    )
