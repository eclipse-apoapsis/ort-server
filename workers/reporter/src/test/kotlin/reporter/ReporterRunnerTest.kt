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

package org.eclipse.apoapsis.ortserver.workers.reporter

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.file.aDirectory
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beInstanceOf

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify

import java.io.File
import java.io.FileNotFoundException

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.PluginConfig
import org.eclipse.apoapsis.ortserver.model.ProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.model.ReportNameMapping
import org.eclipse.apoapsis.ortserver.model.ReporterAsset
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.workers.common.OrtTestData
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.ossreviewtoolkit.model.EvaluatorRun
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Severity as OrtSeverity
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.plugins.api.PluginConfig as OrtPluginConfig
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.reporter.DefaultLicenseTextProvider
import org.ossreviewtoolkit.reporter.HowToFixTextProvider
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput

private const val RUN_ID = 20230522093727L
private val configurationContext = Context("theConfigContext")

@Suppress("LargeClass")
class ReporterRunnerTest : WordSpec({
    afterEach {
        unmockkAll()
    }

    val configManager = mockk<ConfigManager> {
        every { getFile(any(), any()) } throws ConfigException("", null)
        every { getFileAsString(any(), any()) } returns ""
    }

    val configDirectory = tempdir()
    val outputDirectory = tempdir()

    /**
     * Return a mock context. The context mock is prepared to create a temporary directory.
     */
    fun mockContext(
        providerPluginConfigs: List<ProviderPluginConfiguration> = emptyList(),
        resolvedProviderPluginConfigs: List<ProviderPluginConfiguration> = providerPluginConfigs
    ): WorkerContext =
        mockk {
            every { ortRun.resolvedJobConfigContext } returns configurationContext.name
            every { createTempDir() } returnsMany listOf(outputDirectory, configDirectory)
            every { close() } just runs
            coEvery { resolvePluginConfigSecrets(any()) } answers {
                val pluginConfigs: Map<String, PluginConfig>? = firstArg()
                pluginConfigs.orEmpty().mapValues { entry ->
                    val resolvedSecrets = entry.value.secrets.mapValues { secretEntry ->
                        "${secretEntry.value}_resolved"
                    }
                    PluginConfig(entry.value.options, resolvedSecrets)
                }
            }
            coEvery { resolveProviderPluginConfigSecrets(providerPluginConfigs) } returns resolvedProviderPluginConfigs
        }

    "run" should {
        "return a result with report format and report names" {
            val storage = mockk<ReportStorage>()
            coEvery { storage.storeReportFiles(any(), any()) } just runs
            val runner = ReporterRunner(storage, configManager, mockk())

            val reportType = "WebApp"
            val mapping = ReportNameMapping("testReport")
            val config = ReporterJobConfiguration(
                formats = listOf(reportType),
                nameMappings = mapOf(reportType to mapping)
            )
            val result = runner.run(RUN_ID, OrtResult.EMPTY, config, null, mockContext())

            result.reports shouldBe mapOf(reportType to listOf("testReport.html"))
            result.issues should beEmpty()

            val slotReports = slot<Map<String, File>>()
            coVerify {
                storage.storeReportFiles(RUN_ID, capture(slotReports))
            }

            val storedReports = slotReports.captured
            storedReports.keys shouldContainExactly listOf("testReport.html")
        }

        "resolve template file references" {
            val templateFileReference1 = "reporter/my-template.ftl"
            val templateFileReference2 = "reporter/my-other-template.ftl"
            val templateFileReference3 = "reporter/another-template.ftl"
            val resolvedTemplatePrefix = "/var/tmp/"

            val plainFormat = "plain"
            val plainReporter = reporterFactoryMock(plainFormat)

            val templateFormat = "template"
            val templateReporter = reporterFactoryMock(templateFormat)

            mockReporterFactoryAll(
                plainFormat to plainReporter,
                templateFormat to templateReporter
            )

            val plainOptions = mapOf("pretty" to "true")
            val jobConfig = ReporterJobConfiguration(
                formats = listOf(plainFormat, templateFormat),
                config = mapOf(
                    plainFormat to PluginConfig(plainOptions, emptyMap()),
                    templateFormat to PluginConfig(
                        mapOf(
                            "ugly" to "false",
                            "templateFile" to "${ReporterComponent.TEMPLATE_REFERENCE}$templateFileReference1",
                            "otherTemplate" to "${ReporterComponent.TEMPLATE_REFERENCE}$templateFileReference2, " +
                                    "${ReporterComponent.TEMPLATE_REFERENCE}$templateFileReference3"
                        ),
                        emptyMap()
                    )
                )
            )

            val context = mockContext()
            coEvery { context.downloadConfigurationFiles(any(), configDirectory) } answers {
                val paths = firstArg<Collection<Path>>()
                paths.associateWith { path -> File(resolvedTemplatePrefix, path.path) }
            }

            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )
            runner.run(RUN_ID, OrtResult.EMPTY, jobConfig, null, context)

            val slotPlainPluginConfiguration = slot<OrtPluginConfig>()
            val slotTemplatePluginConfiguration = slot<OrtPluginConfig>()
            verify {
                plainReporter.create(capture(slotPlainPluginConfiguration))
                templateReporter.create(capture(slotTemplatePluginConfiguration))
            }

            val expectedTemplateOptions = mapOf(
                "ugly" to "false",
                "templateFile" to "$resolvedTemplatePrefix$templateFileReference1",
                "otherTemplate" to "$resolvedTemplatePrefix$templateFileReference2," +
                        "$resolvedTemplatePrefix$templateFileReference3"
            )
            slotPlainPluginConfiguration.captured.options shouldBe plainOptions
            slotTemplatePluginConfiguration.captured.options shouldBe expectedTemplateOptions
        }

        "handle template file references and other references" {
            val fileReference = "reporter/my-template.ftl"
            val otherReference1 = "foo.ftl"
            val otherReference2 = "bar.ftl"
            val resolvedTemplatePrefix = "/var/tmp/"

            val templateFormat = "template"
            val templateReporter = reporterFactoryMock(templateFormat)

            mockReporterFactoryAll(templateFormat to templateReporter)

            val jobConfig = ReporterJobConfiguration(
                formats = listOf(templateFormat),
                config = mapOf(
                    templateFormat to PluginConfig(
                        mapOf(
                            "templateFile" to
                                    "$otherReference1,${ReporterComponent.TEMPLATE_REFERENCE}$fileReference," +
                                    otherReference2
                        ),
                        emptyMap()
                    )
                )
            )

            val context = mockContext()
            coEvery { context.downloadConfigurationFiles(any(), configDirectory) } answers {
                val paths = firstArg<Collection<Path>>()
                paths.associateWith { path -> File(resolvedTemplatePrefix, path.path) }
            }

            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )
            runner.run(RUN_ID, OrtResult.EMPTY, jobConfig, null, context)

            val slotPluginConfiguration = slot<OrtPluginConfig>()
            verify {
                templateReporter.create(capture(slotPluginConfiguration))
            }

            val expectedTemplateOptions = mapOf(
                "templateFile" to "$otherReference1,$resolvedTemplatePrefix$fileReference,$otherReference2"
            )
            slotPluginConfiguration.captured.options shouldBe expectedTemplateOptions
        }

        "resolve the placeholder for the current working directory in reporter options" {
            val templateFormat = "testTemplate"
            val templateReporter = reporterFactoryMock(templateFormat)

            mockReporterFactoryAll(templateFormat to templateReporter)

            val jobConfig = ReporterJobConfiguration(
                formats = listOf(templateFormat),
                config = mapOf(
                    templateFormat to PluginConfig(
                        mapOf("currentWorkingDir" to "${ReporterComponent.WORK_DIR_PLACEHOLDER}/reports"),
                        emptyMap()
                    )
                )
            )

            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )
            runner.run(RUN_ID, OrtResult.EMPTY, jobConfig, null, mockContext())

            val slotPluginConfiguration = slot<OrtPluginConfig>()
            verify {
                templateReporter.create(capture(slotPluginConfiguration))
            }

            val expectedTemplateOptions = mapOf(
                "currentWorkingDir" to "${configDirectory.absolutePath}/reports"
            )
            slotPluginConfiguration.captured.options shouldBe expectedTemplateOptions
        }

        "resolve secrets in the plugin configurations" {
            val templateFormat = "testSecretTemplate"
            val templateReporter = reporterFactoryMock(templateFormat)

            mockReporterFactoryAll(templateFormat to templateReporter)

            val options = mapOf("foo" to "bar")
            val secrets = mapOf("username" to "secretUsername", "password" to "secretPassword")
            val resolvedSecrets = secrets.mapValues { e -> e.value + "_resolved" }

            val jobConfig = ReporterJobConfiguration(
                formats = listOf(templateFormat),
                config = mapOf(templateFormat to PluginConfig(options, secrets))
            )

            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )
            runner.run(RUN_ID, OrtResult.EMPTY, jobConfig, null, mockContext())

            val slotPluginConfiguration = slot<OrtPluginConfig>()
            verify {
                templateReporter.create(capture(slotPluginConfiguration))
            }

            slotPluginConfiguration.captured shouldBe OrtPluginConfig(options, resolvedSecrets)
        }

        "handle a failed reporter" {
            val failureReportFormat = "IWillFail:-("
            val successReportFormat = "IWillSucceed:-)"

            val failureReporter = reporterFactoryMock(failureReportFormat) {
                every {
                    generateReport(any(), any())
                } returns listOf(Result.failure(FileNotFoundException("Something went wrong...")))
            }

            val successReport = tempfile()
            val successReporter = reporterFactoryMock(successReportFormat) {
                every { generateReport(any(), any()) } returns listOf(Result.success(successReport))
            }

            mockReporterFactoryAll(failureReportFormat to failureReporter, successReportFormat to successReporter)

            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )

            val result = runner.run(
                RUN_ID,
                OrtResult.EMPTY,
                ReporterJobConfiguration(formats = listOf(failureReportFormat, successReportFormat)),
                null,
                mockContext()
            )

            result.reports shouldBe mapOf(successReportFormat to listOf(successReport.name))
            with(result.issues.single()) {
                message shouldContain "Something went wrong"
                message shouldContain failureReportFormat
                source shouldBe "Reporter"
                severity shouldBe Severity.ERROR
            }
        }

        "handle an unknown report format" {
            val unsupportedReportFormat = "UnknownFormat"
            val supportedReportFormat = "supportedFormat"
            val generatedReport = tempfile()
            val supportedReporter = reporterFactoryMock(supportedReportFormat) {
                every { generateReport(any(), any()) } returns listOf(Result.success(generatedReport))
            }

            mockReporterFactoryAll(supportedReportFormat to supportedReporter)

            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )

            val result = runner.run(
                RUN_ID,
                OrtResult.EMPTY,
                ReporterJobConfiguration(formats = listOf(unsupportedReportFormat, supportedReportFormat)),
                null,
                mockContext()
            )

            result.reports shouldBe mapOf(supportedReportFormat to listOf(generatedReport.name))
            with(result.issues.single()) {
                message shouldContain "No reporter found"
                message shouldContain unsupportedReportFormat
                source shouldBe "Reporter"
                severity shouldBe Severity.ERROR
            }
        }

        "use the package configurations resolved by the evaluator" {
            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )

            val format = "format"
            val reporterInputSlot = slot<ReporterInput>()
            val reporter = reporterFactoryMock(format) {
                every { generateReport(capture(reporterInputSlot), any()) } returns emptyList()
            }

            mockReporterFactoryAll(format to reporter)

            val result = runner.run(
                runId = RUN_ID,
                ortResult = OrtTestData.result,
                config = ReporterJobConfiguration(formats = listOf(format)),
                evaluatorConfig = EvaluatorJobConfiguration(),
                mockContext()
            )

            result.resolvedPackageConfigurations should beNull()

            reporterInputSlot.isCaptured shouldBe true
            val resolvedLicense =
                reporterInputSlot.captured.licenseInfoResolver.resolveLicenseInfo(OrtTestData.pkgIdentifier)

            val expectedLicenseFindingCurations =
                OrtTestData.result.repository.config.packageConfigurations.flatMap { it.licenseFindingCurations }
            val actualLicenseFindingCurations =
                resolvedLicense.licenseInfo.detectedLicenseInfo.findings.flatMap { it.licenseFindingCurations }

            actualLicenseFindingCurations should containExactlyInAnyOrder(expectedLicenseFindingCurations)

            val expectedPathExcludes =
                OrtTestData.result.repository.config.packageConfigurations.flatMap { it.pathExcludes }
            val actualPathExcludes =
                resolvedLicense.licenseInfo.detectedLicenseInfo.findings.flatMap { it.pathExcludes }

            actualPathExcludes should containExactlyInAnyOrder(expectedPathExcludes)
        }

        "resolve package configurations if no evaluator job is configured" {
            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )

            val format = "format"
            val reporter = reporterFactoryMock(format)

            mockReporterFactoryAll(format to reporter)

            val result = runner.run(
                runId = RUN_ID,
                ortResult = OrtTestData.result,
                config = ReporterJobConfiguration(formats = listOf(format)),
                evaluatorConfig = null,
                mockContext()
            )

            result.resolvedPackageConfigurations should
                    containExactlyInAnyOrder(OrtTestData.result.repository.config.packageConfigurations)
        }

        "resolve secrets in the package configuration provider configurations" {
            mockkObject(PackageConfigurationProviderFactory)
            every { PackageConfigurationProviderFactory.create(any()) } returns mockk(relaxed = true)

            val packageConfigurationProviderConfigs = listOf(
                ProviderPluginConfiguration(
                    type = "Dir",
                    options = mapOf("path" to "path1"),
                    secrets = mapOf("secret1" to "ref1")
                ),
                ProviderPluginConfiguration(
                    type = "Dir",
                    options = mapOf("path" to "path2"),
                    secrets = mapOf("secret2" to "ref2")
                )
            )

            val resolvedPackageConfigurationProviderConfigs = listOf(
                ProviderPluginConfiguration(
                    type = "Dir",
                    options = mapOf("path" to "path1"),
                    secrets = mapOf("secret1" to "value1")
                ),
                ProviderPluginConfiguration(
                    type = "Dir",
                    options = mapOf("path" to "path2"),
                    secrets = mapOf("secret2" to "value2")
                )
            )

            val context = mockContext(
                packageConfigurationProviderConfigs,
                resolvedPackageConfigurationProviderConfigs
            )

            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )

            val format = "format"
            val reporter = reporterFactoryMock(format)

            mockReporterFactoryAll(format to reporter)

            runner.run(
                runId = RUN_ID,
                ortResult = OrtTestData.result,
                config = ReporterJobConfiguration(
                    formats = listOf(format),
                    packageConfigurationProviders = packageConfigurationProviderConfigs
                ),
                evaluatorConfig = null,
                context
            )

            verify(exactly = 1) {
                PackageConfigurationProviderFactory.create(
                    resolvedPackageConfigurationProviderConfigs.map { it.mapToOrt() }
                )
            }
        }

        "use the resolutions resolved by the evaluator" {
            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )

            val format = "format"
            val reporterInputSlot = slot<ReporterInput>()
            val reporter = reporterFactoryMock(format) {
                every { generateReport(capture(reporterInputSlot), any()) } returns emptyList()
            }

            mockReporterFactoryAll(format to reporter)

            val ruleViolation = RuleViolation("RULE", null, null, null, OrtSeverity.ERROR, "message", "howToFix")

            val result = runner.run(
                runId = RUN_ID,
                ortResult = OrtTestData.result.copy(
                    evaluator = EvaluatorRun(
                        startTime = Clock.System.now().toJavaInstant(),
                        endTime = Clock.System.now().toJavaInstant(),
                        violations = listOf(ruleViolation)
                    )
                ),
                config = ReporterJobConfiguration(formats = listOf(format)),
                evaluatorConfig = EvaluatorJobConfiguration(),
                mockContext()
            )

            result.resolvedResolutions should beNull()

            reporterInputSlot.isCaptured shouldBe true
            DefaultResolutionProvider(reporterInputSlot.captured.ortResult.repository.config.resolutions).apply {
                isResolved(OrtTestData.issue) shouldBe true
                isResolved(ruleViolation) shouldBe true
                isResolved(OrtTestData.vulnerability) shouldBe true
            }
        }

        "resolve resolutions if no evaluator job is configured" {
            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )

            val format = "format"
            val reporter = reporterFactoryMock(format)

            mockReporterFactoryAll(format to reporter)

            val result = runner.run(
                runId = RUN_ID,
                ortResult = OrtTestData.result.copy(
                    evaluator = EvaluatorRun(
                        startTime = Clock.System.now().toJavaInstant(),
                        endTime = Clock.System.now().toJavaInstant(),
                        violations = listOf(
                            RuleViolation("RULE", null, null, null, OrtSeverity.ERROR, "message", "howToFix")
                        )
                    )
                ),
                config = ReporterJobConfiguration(formats = listOf(format)),
                evaluatorConfig = null,
                mockContext()
            )

            result.resolvedResolutions shouldBe OrtTestData.result.repository.config.resolutions
        }

        "download asset files" {
            val format = "testAssetFiles"
            val reporter = reporterFactoryMock(format)

            mockReporterFactoryAll(format to reporter)

            val assetFiles = listOf(
                ReporterAsset("logo.png"),
                ReporterAsset("nice.ft", "fonts"),
                ReporterAsset("evenNicer.ft", "fonts", "nice2.ft")
            )
            val jobConfig = ReporterJobConfiguration(
                formats = listOf(format),
                assetFiles = assetFiles
            )

            val downloadedAssets = mutableListOf<ReporterAsset>()
            val context = mockContext()
            coEvery { context.downloadConfigurationFile(any(), any(), any()) } answers {
                val path = firstArg<String>()
                val dir = secondArg<File>()
                val relativeDir = if (dir == configDirectory) {
                    null
                } else {
                    dir.parentFile shouldBe configDirectory
                    dir shouldBe aDirectory()
                    dir.name
                }
                downloadedAssets += ReporterAsset(path, relativeDir, thirdArg())
                File(path)
            }

            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )
            runner.run(RUN_ID, OrtResult.EMPTY, jobConfig, null, context)

            downloadedAssets shouldContainExactlyInAnyOrder assetFiles
        }

        "download asset directories" {
            val format = "testAssetDirectories"
            val reporter = reporterFactoryMock(format)

            mockReporterFactoryAll(format to reporter)

            val assetDirectories = listOf(
                ReporterAsset("data"),
                ReporterAsset("images", "imgs", "ignored")
            )
            val jobConfig = ReporterJobConfiguration(
                formats = listOf(format),
                assetDirectories = assetDirectories
            )

            val resolvedContext = Context("theResolvedContext")

            val downloadedAssets = mutableMapOf<Path, File>()
            val context = mockContext()
            every { context.ortRun.resolvedJobConfigContext } returns resolvedContext.name
            every { context.configManager } returns configManager
            coEvery { context.downloadConfigurationDirectory(any(), any()) } answers {
                val path = Path(firstArg<String>())
                val dir = secondArg<File>()
                dir shouldBe aDirectory()
                downloadedAssets[path] = dir
                mapOf(path to File(path.path))
            }

            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )
            runner.run(RUN_ID, OrtResult.EMPTY, jobConfig, null, context)

            downloadedAssets.keys shouldHaveSize 2
            downloadedAssets[Path("data")] shouldBe configDirectory

            downloadedAssets[Path("images")] shouldNotBeNull {
                parentFile shouldBe configDirectory
                name shouldBe "imgs"
            }
        }

        "configure a correct custom license text provider" {
            val format = "testCustomLicenseTexts"
            val reporterInputSlot = slot<ReporterInput>()
            val reporter = reporterFactoryMock(format) {
                every { generateReport(capture(reporterInputSlot), any()) } returns emptyList()
            }

            mockReporterFactoryAll(format to reporter)

            val customLicenseTextsPath = "custom-license-texts"
            val jobConfig = ReporterJobConfiguration(
                formats = listOf(format),
                customLicenseTextDir = customLicenseTextsPath
            )

            val context = mockContext()
            every { context.configManager } returns configManager

            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )
            runner.run(RUN_ID, OrtResult.EMPTY, jobConfig, null, context)

            // Verify that a correct provider was passed to the reporter input.
            reporterInputSlot.isCaptured shouldBe true
            with(reporterInputSlot.captured.licenseTextProvider as CustomLicenseTextProvider) {
                licenseTextDir shouldBe Path(customLicenseTextsPath)
            }
        }

        "use the configured how-to-fix text provider" {
            val format = "testHowToFixTextProvider"
            val howToFixTextProviderFile = "testHowToFixTextProvider.kts"
            val howToFixTextProviderScript = "How-To-Fix Kotlin Script"
            val jobConfig = ReporterJobConfiguration(
                formats = listOf(format),
                howToFixTextProviderFile = howToFixTextProviderFile
            )

            val reporterInputSlot = slot<ReporterInput>()
            val reporter = reporterFactoryMock(format) {
                every { generateReport(capture(reporterInputSlot), any()) } returns emptyList()
            }

            mockReporterFactoryAll(format to reporter)

            // Mock the HowToFixTextProvider.fromKotlinScript function to return the mocked object.
            val mockHowToFixTextProvider = HowToFixTextProvider { "A test How-To-Fix text." }
            mockkObject(HowToFixTextProvider)
            every {
                HowToFixTextProvider.fromKotlinScript(howToFixTextProviderScript, any())
            } returns mockHowToFixTextProvider

            every {
                configManager.getFile(any(), Path(howToFixTextProviderFile))
            } returns howToFixTextProviderScript.byteInputStream()

            val resolvedContext = Context("theResolvedContext")

            val context = mockContext()
            every { context.ortRun.resolvedJobConfigContext } returns resolvedContext.name
            every { context.configManager } returns configManager

            val runner = ReporterRunner(
                mockk(relaxed = true),
                configManager,
                mockk()
            )
            runner.run(RUN_ID, OrtResult.EMPTY, jobConfig, null, context)

            reporterInputSlot.isCaptured shouldBe true
            reporterInputSlot.captured.howToFixTextProvider shouldBe mockHowToFixTextProvider
            reporterInputSlot.captured.howToFixTextProvider.getHowToFixText(
                issue = Issue(message = "Test issue message.", source = "Test")
            ) shouldBe "A test How-To-Fix text."
        }
    }

    "createLicenseTextProvider" should {
        "create a CustomLicenseTextProvider if a custom license text directory is configured" {
            val testConfigContext = "configurationContext"
            val customLicenseTextDir = "path/to/custom/licenses"
            val reporterConfig = ReporterJobConfiguration(customLicenseTextDir = customLicenseTextDir)
            val run = OrtRun(
                id = 1,
                index = 2,
                organizationId = 3,
                productId = 4,
                repositoryId = 5,
                createdAt = Instant.parse("2024-12-06T05:40:00Z"),
                revision = "test",
                finishedAt = null,
                jobConfigs = JobConfigurations(),
                resolvedJobConfigs = null,
                status = OrtRunStatus.ACTIVE,
                vcsId = null,
                vcsProcessedId = null,
                nestedRepositoryIds = null,
                labels = emptyMap(),
                repositoryConfigId = null,
                jobConfigContext = null,
                issues = emptyList(),
                traceId = null,
                resolvedJobConfigContext = testConfigContext
            )

            val context = mockk<WorkerContext> {
                every { ortRun } returns run
                every { this@mockk.configManager } returns configManager
            }

            val provider = createLicenseTextProvider(context, reporterConfig)

            with(provider as CustomLicenseTextProvider) {
                this.configManager shouldBe configManager
                this.configurationContext?.name shouldBe testConfigContext
                licenseTextDir shouldBe Path(customLicenseTextDir)
                wrappedProvider should beInstanceOf<DefaultLicenseTextProvider>()
            }
        }

        "create a DefaultLicenseTextProvider if no custom license text directory is configured" {
            val reporterConfig = ReporterJobConfiguration()
            val context = mockk<WorkerContext>()

            val provider = createLicenseTextProvider(context, reporterConfig)

            provider should beInstanceOf<DefaultLicenseTextProvider>()
        }
    }
})

/**
 * Create a mock [ReporterFactory] that is prepared to create a [Reporter] with the given [plugin ID][reporterId]. The
 * [block] is used to further configure the created [Reporter]. By default, the [block] configures
 * [Reporter.generateReport] to return an empty list of reports.
 */
private fun reporterFactoryMock(
    reporterId: String,
    block: Reporter.() -> Unit = {
        every { generateReport(any(), any()) } returns emptyList()
    }
): ReporterFactory =
    mockk {
        every { descriptor } returns mockk { every { id } returns reporterId }
        every { create(any()) } returns mockk {
            every { descriptor } returns mockk { every { id } returns reporterId }
            block()
        }
    }

/**
 * Mock the list of available reporter factories in the [ReporterFactory] companion object to the given
 * [reporterFactories].
 */
private fun mockReporterFactoryAll(vararg reporterFactories: Pair<String, ReporterFactory>) {
    mockkObject(ReporterFactory)
    every { ReporterFactory.ALL } returns sortedMapOf(*reporterFactories)
}
