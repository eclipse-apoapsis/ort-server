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
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.file.aDirectory
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

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

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

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
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.ResolvablePluginConfig
import org.eclipse.apoapsis.ortserver.model.ResolvableProviderPluginConfig
import org.eclipse.apoapsis.ortserver.model.ResolvableSecret
import org.eclipse.apoapsis.ortserver.model.SecretSource
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolutionReason
import org.eclipse.apoapsis.ortserver.model.runs.repository.Resolutions
import org.eclipse.apoapsis.ortserver.services.config.AdminConfig
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.services.config.ReportDefinition
import org.eclipse.apoapsis.ortserver.services.config.ReportNameMapping
import org.eclipse.apoapsis.ortserver.services.config.ReporterAsset
import org.eclipse.apoapsis.ortserver.services.config.ReporterConfig
import org.eclipse.apoapsis.ortserver.services.config.RuleSet
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.shared.orttestdata.OrtTestData
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

import org.ossreviewtoolkit.model.EvaluatorRun
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Severity as OrtSeverity
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.licenses.LicenseCategory
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.plugins.api.PluginConfig as OrtPluginConfig
import org.ossreviewtoolkit.plugins.licensefactproviders.api.CompositeLicenseFactProvider
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProvider
import org.ossreviewtoolkit.plugins.licensefactproviders.scancode.ScanCodeLicenseFactProvider
import org.ossreviewtoolkit.plugins.licensefactproviders.spdx.SpdxLicenseFactProvider
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.reporter.HowToFixTextProvider
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.enumSetOf
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.ort.ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME

private const val RUN_ID = 20230522093727L
private const val ORGANIZATION_ID = 20250707045021L
private const val RULE_SET_NAME = "selectedRuleSet"
private val configurationContext = Context("theConfigContext")

private val ruleSet = RuleSet(
    copyrightGarbageFile = "testCopyrightGarbageFile",
    licenseClassificationsFile = "testLicenseClassificationsFile",
    resolutionsFile = "testResolutionsFile",
    evaluatorRules = "testEvaluatorRulesFile"
)

private val copyrightGarbage = CopyrightGarbage(setOf("copyrightFoo", "copyrightBar"))

private val licenseClassifications = LicenseClassifications(
    categories = listOf(LicenseCategory("testLicense"))
)

private val resolutions = Resolutions(
    issues = listOf(IssueResolution("message", IssueResolutionReason.CANT_FIX_ISSUE, "comment"))
)

/** Name of a test reporter format. */
private const val TEST_REPORT_FORMAT = "testFormat"

/**
 * A test reporter configuration that contains only a dummy definition for the test format. This can be used to test
 * functionality not related to the actual report generation.
 */
private val testReportConfig = createReporterConfig(
    reportDefinitions = arrayOf(createReportDefinition(TEST_REPORT_FORMAT))
)

@Suppress("LargeClass")
class ReporterRunnerTest : WordSpec({
    afterEach {
        unmockkAll()
    }

    val configManager = mockk<ConfigManager> {
        every { getFile(configurationContext, any()) } answers {
            simulateGetConfigFile(secondArg())
        }
        every { getFileAsString(any(), any()) } returns ""
    }

    val configDirectory = tempdir()
    val outputDirectory = tempdir()

    /**
     * Return a mock context. The context mock is prepared to create a temporary directory and return some default
     * data.
     */
    fun mockContext(
        providerPluginConfigs: List<ResolvableProviderPluginConfig> = emptyList(),
        resolvedProviderPluginConfigs: List<ProviderPluginConfiguration> = emptyList()
    ): WorkerContext =
        mockk {
            every { this@mockk.configManager } returns configManager
            every { ortRun.id } returns RUN_ID
            every { ortRun.organizationId } returns ORGANIZATION_ID
            every { ortRun.resolvedJobConfigContext } returns configurationContext.name
            every { ortRun.resolvedJobConfigs } returns JobConfigurations(ruleSet = RULE_SET_NAME)
            every { createTempDir() } returnsMany listOf(outputDirectory, configDirectory)
            every { close() } just runs
            coEvery { resolvePluginConfigSecrets(any()) } answers {
                val pluginConfigs: Map<String, ResolvablePluginConfig>? = firstArg()
                pluginConfigs.orEmpty().mapValues { entry ->
                    val resolvedSecrets = entry.value.secrets.mapValues { secretEntry ->
                        "${secretEntry.value.name}_resolved"
                    }
                    PluginConfig(entry.value.options, resolvedSecrets)
                }
            }
            coEvery { resolveProviderPluginConfigSecrets(providerPluginConfigs) } returns resolvedProviderPluginConfigs
        }

    /**
     * Create a [ReporterRunner] for testing that is initialized with the given [storage] and uses the given [config].
     */
    fun createRunner(
        storage: ReportStorage = mockk(relaxed = true),
        config: ReporterConfig = createReporterConfig()
    ): ReporterRunner =
        ReporterRunner(storage, mockk(), createAdminConfigService(config))

    "run" should {
        "return a result with report format and report names" {
            val storage = mockk<ReportStorage>()
            coEvery { storage.storeReportFiles(any(), any()) } just runs

            val reportType = "WebApp"
            val mapping = ReportNameMapping("testReport")
            val reporterConfig = createReporterConfig(
                reportDefinitions = arrayOf(createReportDefinition(reportType, nameMapping = mapping))
            )
            val runner = createRunner(storage, reporterConfig)

            val config = ReporterJobConfiguration(
                formats = listOf(reportType)
            )
            val result = runner.run(OrtResult.EMPTY, config, null, mockContext())

            result.reports should containExactly("testReport.html")
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
            val plainPluginId = "plainPlugin"
            val plainReporter = reporterFactoryMock(plainPluginId)

            val templateFormat = "template"
            val templatePluginId = "templatePlugin"
            val templateReporter = reporterFactoryMock(templatePluginId)

            mockReporterFactoryAll(
                plainPluginId to plainReporter,
                templatePluginId to templateReporter
            )

            val plainOptions = mapOf("pretty" to "true")
            val jobConfig = ReporterJobConfiguration(
                formats = listOf(plainFormat, templateFormat),
                config = mapOf(
                    plainPluginId to ResolvablePluginConfig(plainOptions, emptyMap()),
                    templatePluginId to ResolvablePluginConfig(
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

            val runner = createRunner(
                config = createReporterConfig(
                    reportDefinitions = arrayOf(
                        createReportDefinition(plainPluginId, plainFormat),
                        createReportDefinition(templatePluginId, templateFormat)
                    )
                )
            )
            runner.run(OrtResult.EMPTY, jobConfig, null, context)

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
                    templateFormat to ResolvablePluginConfig(
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

            val reporterConfig = createReporterConfig(
                reportDefinitions = arrayOf(createReportDefinition(templateFormat))
            )
            val runner = createRunner(config = reporterConfig)
            runner.run(OrtResult.EMPTY, jobConfig, null, context)

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
                    templateFormat to ResolvablePluginConfig(
                        mapOf("currentWorkingDir" to "${ReporterComponent.WORK_DIR_PLACEHOLDER}/reports"),
                        emptyMap()
                    )
                )
            )

            val reporterConfig = createReporterConfig(
                reportDefinitions = arrayOf(createReportDefinition(templateFormat))
            )
            val runner = createRunner(config = reporterConfig)
            runner.run(OrtResult.EMPTY, jobConfig, null, mockContext())

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
            val secrets = mapOf(
                "username" to ResolvableSecret("secretUsername", SecretSource.ADMIN),
                "password" to ResolvableSecret("secretPassword", SecretSource.ADMIN)
            )
            val resolvedSecrets = secrets.mapValues { e -> e.value.name + "_resolved" }

            val jobConfig = ReporterJobConfiguration(
                formats = listOf(templateFormat),
                config = mapOf(templateFormat to ResolvablePluginConfig(options, secrets))
            )

            val reporterConfig = createReporterConfig(
                reportDefinitions = arrayOf(createReportDefinition(templateFormat))
            )
            val runner = createRunner(config = reporterConfig)
            runner.run(OrtResult.EMPTY, jobConfig, null, mockContext())

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

            val reporterConfig = createReporterConfig(
                reportDefinitions = arrayOf(
                    createReportDefinition(failureReportFormat),
                    createReportDefinition(successReportFormat)
                )
            )
            val runner = createRunner(config = reporterConfig)

            val result = runner.run(
                OrtResult.EMPTY,
                ReporterJobConfiguration(formats = listOf(failureReportFormat, successReportFormat)),
                null,
                mockContext()
            )

            result.reports should containExactly(successReport.name)
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

            val reporterConfig = createReporterConfig(
                reportDefinitions = arrayOf(createReportDefinition(supportedReportFormat))
            )
            val runner = createRunner(config = reporterConfig)

            val result = runner.run(
                OrtResult.EMPTY,
                ReporterJobConfiguration(formats = listOf(unsupportedReportFormat, supportedReportFormat)),
                null,
                mockContext()
            )

            result.reports should containExactly(generatedReport.name)
            with(result.issues.single()) {
                message shouldContain "No reporter found"
                message shouldContain unsupportedReportFormat
                source shouldBe "Reporter"
                severity shouldBe Severity.ERROR
            }
        }

        "handle an unknown reporter plugin" {
            val unsupportedReportFormat = "UnknownFormat"
            val supportedReportFormat = "supportedFormat"
            val unsupportedPlugin = "unsupportedPlugin"
            val generatedReport = tempfile()
            val supportedReporter = reporterFactoryMock(supportedReportFormat) {
                every { generateReport(any(), any()) } returns listOf(Result.success(generatedReport))
            }

            mockReporterFactoryAll(supportedReportFormat to supportedReporter)

            val reporterConfig = createReporterConfig(
                reportDefinitions = arrayOf(
                    createReportDefinition(supportedReportFormat),
                    createReportDefinition(unsupportedPlugin, unsupportedReportFormat)
                )
            )
            val runner = createRunner(config = reporterConfig)

            val result = runner.run(
                OrtResult.EMPTY,
                ReporterJobConfiguration(formats = listOf(unsupportedReportFormat, supportedReportFormat)),
                null,
                mockContext()
            )

            result.reports should containExactly(generatedReport.name)
            with(result.issues.single()) {
                message shouldContain "No reporter plugin found"
                message shouldContain unsupportedPlugin
                source shouldBe "Reporter"
                severity shouldBe Severity.ERROR
            }
        }

        "use the package configurations resolved by the evaluator" {
            val reporterInputSlot = slot<ReporterInput>()
            val reporter = reporterFactoryMock(TEST_REPORT_FORMAT) {
                every { generateReport(capture(reporterInputSlot), any()) } returns emptyList()
            }

            mockReporterFactoryAll(TEST_REPORT_FORMAT to reporter)

            val runner = createRunner(config = testReportConfig)
            val result = runner.run(
                ortResult = OrtTestData.result,
                config = ReporterJobConfiguration(formats = listOf(TEST_REPORT_FORMAT)),
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
            val runner = createRunner(config = testReportConfig)

            val reporter = reporterFactoryMock(TEST_REPORT_FORMAT)
            mockReporterFactoryAll(TEST_REPORT_FORMAT to reporter)

            val result = runner.run(
                ortResult = OrtTestData.result,
                config = ReporterJobConfiguration(formats = listOf(TEST_REPORT_FORMAT)),
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
                ResolvableProviderPluginConfig(
                    type = "Dir",
                    options = mapOf("path" to "path1"),
                    secrets = mapOf("secret1" to ResolvableSecret("ref1", SecretSource.ADMIN))
                ),
                ResolvableProviderPluginConfig(
                    type = "Dir",
                    options = mapOf("path" to "path2"),
                    secrets = mapOf("secret2" to ResolvableSecret("ref2", SecretSource.ADMIN))
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

            val runner = createRunner(config = testReportConfig)

            val reporter = reporterFactoryMock(TEST_REPORT_FORMAT)
            mockReporterFactoryAll(TEST_REPORT_FORMAT to reporter)

            runner.run(
                ortResult = OrtTestData.result,
                config = ReporterJobConfiguration(
                    formats = listOf(TEST_REPORT_FORMAT),
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
            val runner = createRunner(config = testReportConfig)

            val reporterInputSlot = slot<ReporterInput>()
            val reporter = reporterFactoryMock(TEST_REPORT_FORMAT) {
                every { generateReport(capture(reporterInputSlot), any()) } returns emptyList()
            }

            mockReporterFactoryAll(TEST_REPORT_FORMAT to reporter)

            val ruleViolation = RuleViolation("RULE", null, null, enumSetOf(), OrtSeverity.ERROR, "message", "howToFix")

            runner.run(
                ortResult = OrtTestData.result.copy(
                    evaluator = EvaluatorRun(
                        startTime = Clock.System.now().toJavaInstant(),
                        endTime = Clock.System.now().toJavaInstant(),
                        environment = Environment(),
                        violations = listOf(ruleViolation)
                    )
                ),
                config = ReporterJobConfiguration(formats = listOf(TEST_REPORT_FORMAT)),
                evaluatorConfig = EvaluatorJobConfiguration(),
                mockContext()
            )

            reporterInputSlot.isCaptured shouldBe true
            DefaultResolutionProvider(reporterInputSlot.captured.ortResult.repository.config.resolutions).apply {
                isResolved(OrtTestData.issue) shouldBe true
                isResolved(ruleViolation) shouldBe true
                isResolved(OrtTestData.vulnerability) shouldBe true
            }
        }

        "resolve resolutions if no evaluator job is configured" {
            val runner = createRunner(config = testReportConfig)

            val reporter = reporterFactoryMock(TEST_REPORT_FORMAT)
            mockReporterFactoryAll(TEST_REPORT_FORMAT to reporter)

            runner.run(
                ortResult = OrtTestData.result.copy(
                    evaluator = EvaluatorRun(
                        startTime = Clock.System.now().toJavaInstant(),
                        endTime = Clock.System.now().toJavaInstant(),
                        environment = Environment(),
                        violations = listOf(
                            RuleViolation("RULE", null, null, enumSetOf(), OrtSeverity.ERROR, "message", "howToFix")
                        )
                    )
                ),
                config = ReporterJobConfiguration(formats = listOf(TEST_REPORT_FORMAT)),
                evaluatorConfig = null,
                mockContext()
            )
        }

        "not return resolvedItems when evaluator ran" {
            val runner = createRunner(config = testReportConfig)

            val reporter = reporterFactoryMock(TEST_REPORT_FORMAT)
            mockReporterFactoryAll(TEST_REPORT_FORMAT to reporter)

            val result = runner.run(
                ortResult = OrtTestData.result,
                config = ReporterJobConfiguration(formats = listOf(TEST_REPORT_FORMAT)),
                evaluatorConfig = EvaluatorJobConfiguration(),
                mockContext()
            )

            result.resolvedItems should beNull()
        }

        "return resolvedItems when evaluator did not run" {
            val runner = createRunner(config = testReportConfig)

            val reporter = reporterFactoryMock(TEST_REPORT_FORMAT)
            mockReporterFactoryAll(TEST_REPORT_FORMAT to reporter)

            val result = runner.run(
                ortResult = OrtTestData.result,
                config = ReporterJobConfiguration(formats = listOf(TEST_REPORT_FORMAT)),
                evaluatorConfig = null,
                mockContext()
            )

            result.resolvedItems shouldNot beNull()
            // resolvedItems should have been computed when evaluator didn't run
            result.resolvedItems!!.issues.isEmpty() shouldBe false
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
                formats = listOf(format)
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

            val reporterConfig = createReporterConfig(
                reportDefinitions = arrayOf(
                    createReportDefinition(format, assetFiles = assetFiles)
                )
            )
            val runner = createRunner(config = reporterConfig)
            runner.run(OrtResult.EMPTY, jobConfig, null, context)

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
                formats = listOf(format)
            )

            val downloadedAssets = mutableMapOf<Path, File>()
            val context = mockContext()
            coEvery { context.downloadConfigurationDirectory(any(), any()) } answers {
                val path = Path(firstArg<String>())
                val dir = secondArg<File>()
                dir shouldBe aDirectory()
                downloadedAssets[path] = dir
                mapOf(path to File(path.path))
            }

            val reporterConfig = createReporterConfig(
                reportDefinitions = arrayOf(
                    createReportDefinition(format, assetDirectories = assetDirectories)
                )
            )
            val runner = createRunner(config = reporterConfig)
            runner.run(OrtResult.EMPTY, jobConfig, null, context)

            downloadedAssets.keys shouldHaveSize 2
            downloadedAssets[Path("data")] shouldBe configDirectory

            downloadedAssets[Path("images")] shouldNotBeNull {
                parentFile shouldBe configDirectory
                name shouldBe "imgs"
            }
        }

        "download named asset files groups" {
            val format = "testAssetFilesGroups"
            val reporter = reporterFactoryMock(format)

            mockReporterFactoryAll(format to reporter)

            val groupName1 = "niceAssets"
            val assetFiles1 = listOf(
                ReporterAsset("logo.png"),
                ReporterAsset("nice.ft", "fonts")
            )
            val groupName2 = "evenNicerAssets"
            val assetFiles2 = listOf(
                ReporterAsset("evenNicer.ft", "fonts", "nice2.ft")
            )
            val jobConfig = ReporterJobConfiguration(
                formats = listOf(format),
                assetFilesGroups = listOf(groupName1, groupName2)
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

            val reporterConfig = createReporterConfig(
                reportDefinitions = arrayOf(
                    createReportDefinition(format)
                )
            ).copy(
                globalAssets = mapOf(
                    groupName1 to assetFiles1,
                    groupName2 to assetFiles2
                )
            )
            val runner = createRunner(config = reporterConfig)
            runner.run(OrtResult.EMPTY, jobConfig, null, context)

            downloadedAssets shouldContainExactlyInAnyOrder assetFiles1 + assetFiles2
        }

        "download named asset directories groups" {
            val format = "testAssetDirectories"
            val reporter = reporterFactoryMock(format)

            mockReporterFactoryAll(format to reporter)

            val groupName1 = "themeAssets"
            val themeDirectories = listOf(
                ReporterAsset("data")
            )
            val groupName2 = "imageAssets"
            val imageDirectories = listOf(
                ReporterAsset("images", "imgs", "ignored")
            )
            val jobConfig = ReporterJobConfiguration(
                formats = listOf(format),
                assetDirectoriesGroups = listOf(groupName1, groupName2)
            )

            val downloadedAssets = mutableMapOf<Path, File>()
            val context = mockContext()
            coEvery { context.downloadConfigurationDirectory(any(), any()) } answers {
                val path = Path(firstArg<String>())
                val dir = secondArg<File>()
                dir shouldBe aDirectory()
                downloadedAssets[path] = dir
                mapOf(path to File(path.path))
            }

            val reporterConfig = createReporterConfig(
                reportDefinitions = arrayOf(
                    createReportDefinition(format)
                )
            ).copy(
                globalAssets = mapOf(
                    groupName1 to themeDirectories,
                    groupName2 to imageDirectories
                )
            )
            val runner = createRunner(config = reporterConfig)
            runner.run(OrtResult.EMPTY, jobConfig, null, context)

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
                formats = listOf(format)
            )

            val context = mockContext()

            val reporterConfig = createReporterConfig(
                customLicenseTextDir = customLicenseTextsPath,
                reportDefinitions = arrayOf(createReportDefinition(format))
            )
            val runner = createRunner(config = reporterConfig)
            runner.run(OrtResult.EMPTY, jobConfig, null, context)

            // Verify that a valid provider was passed to the reporter input.
            reporterInputSlot.isCaptured shouldBe true
            with(reporterInputSlot.captured.licenseFactProvider as CompositeLicenseFactProvider) {
                val provider = getProviders().single { it is CustomLicenseFactProvider } as CustomLicenseFactProvider
                provider.licenseTextDir shouldBe Path(customLicenseTextsPath)
            }
        }

        "use the configured how-to-fix text provider" {
            val format = "testHowToFixTextProvider"
            val howToFixTextProviderFile = "testHowToFixTextProvider.kts"
            val howToFixTextProviderScript = "How-To-Fix Kotlin Script"
            val jobConfig = ReporterJobConfiguration(
                formats = listOf(format)
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

            val context = mockContext()

            val reporterConfig = createReporterConfig(
                howToFixTextProviderFile = howToFixTextProviderFile,
                reportDefinitions = arrayOf(createReportDefinition(format))
            )
            val runner = createRunner(config = reporterConfig)
            runner.run(OrtResult.EMPTY, jobConfig, null, context)

            reporterInputSlot.isCaptured shouldBe true
            reporterInputSlot.captured.howToFixTextProvider shouldBe mockHowToFixTextProvider
            reporterInputSlot.captured.howToFixTextProvider.getHowToFixText(
                issue = Issue(message = "Test issue message.", source = "Test")
            ) shouldBe "A test How-To-Fix text."
        }

        "use the configured copyright garbage" {
            val format = "testCopyrightGarbage"
            val jobConfig = ReporterJobConfiguration(
                formats = listOf(format)
            )

            val reporterInputSlot = slot<ReporterInput>()
            val reporter = reporterFactoryMock(format) {
                every { generateReport(capture(reporterInputSlot), any()) } returns emptyList()
            }

            mockReporterFactoryAll(format to reporter)

            val context = mockContext()

            val reporterConfig = createReporterConfig(
                reportDefinitions = arrayOf(createReportDefinition(format))
            )
            val runner = createRunner(config = reporterConfig)
            runner.run(OrtResult.EMPTY, jobConfig, null, context)

            reporterInputSlot.captured.copyrightGarbage shouldBe copyrightGarbage
        }

        "use the configured license classifications" {
            val format = "testLicenseClassifications"
            val jobConfig = ReporterJobConfiguration(
                formats = listOf(format)
            )

            val reporterInputSlot = slot<ReporterInput>()
            val reporter = reporterFactoryMock(format) {
                every { generateReport(capture(reporterInputSlot), any()) } returns emptyList()
            }

            mockReporterFactoryAll(format to reporter)

            val context = mockContext()

            val reporterConfig = createReporterConfig(
                reportDefinitions = arrayOf(createReportDefinition(format))
            )
            val runner = createRunner(config = reporterConfig)
            runner.run(OrtResult.EMPTY, jobConfig, null, context)

            reporterInputSlot.captured.licenseClassifications shouldBe licenseClassifications
        }

        "merge plugin configurations with configurations for report definitions" {
            val templateFormat = "specialDocument"
            val templatePluginId = "templatePlugin"
            val templateReporter = reporterFactoryMock(templatePluginId)

            mockReporterFactoryAll(
                templatePluginId to templateReporter
            )

            val jobConfig = ReporterJobConfiguration(
                formats = listOf(templateFormat),
                config = mapOf(
                    templatePluginId to ResolvablePluginConfig(
                        mapOf(
                            "ugly" to "false",
                            "templateFile" to "someTemplate.ftl"
                        ),
                        emptyMap()
                    ),
                    "$templatePluginId:$templateFormat" to ResolvablePluginConfig(
                        mapOf(
                            "templateFile" to "anotherTemplate.ftl",
                            "specialProperty" to "specialValue"
                        ),
                        emptyMap()
                    )
                )
            )

            val context = mockContext()
            val runner = createRunner(
                config = createReporterConfig(
                    reportDefinitions = arrayOf(
                        createReportDefinition(templatePluginId, templateFormat)
                    )
                )
            )
            runner.run(OrtResult.EMPTY, jobConfig, null, context)

            val slotTemplatePluginConfiguration = slot<OrtPluginConfig>()
            verify {
                templateReporter.create(capture(slotTemplatePluginConfiguration))
            }

            val expectedTemplateOptions = mapOf(
                "ugly" to "false",
                "templateFile" to "anotherTemplate.ftl",
                "specialProperty" to "specialValue"
            )
            slotTemplatePluginConfiguration.captured.options shouldBe expectedTemplateOptions
        }
    }

    "createLicenseFactProvider" should {
        "create a CustomLicenseTextProvider and default providers if a custom license text directory is configured" {
            val testConfigContext = "configurationContext"
            val customLicenseTextDir = "path/to/custom/licenses"
            val reporterConfig = createReporterConfig(customLicenseTextDir = customLicenseTextDir)
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

            val provider = createLicenseFactProvider(context, reporterConfig)

            with(provider.getProviders().single { it is CustomLicenseFactProvider } as CustomLicenseFactProvider) {
                this.configManager shouldBe configManager
                this.configurationContext?.name shouldBe testConfigContext
                licenseTextDir shouldBe Path(customLicenseTextDir)
            }

            provider.getProviders().find { it is SpdxLicenseFactProvider } shouldNot beNull()
            provider.getProviders().find { it is ScanCodeLicenseFactProvider } shouldNot beNull()
        }

        "create the default providers" {
            val reporterConfig = createReporterConfig()
            val context = mockk<WorkerContext>()

            val provider = createLicenseFactProvider(context, reporterConfig)

            provider.getProviders().find { it is SpdxLicenseFactProvider } shouldNot beNull()
            provider.getProviders().find { it is ScanCodeLicenseFactProvider } shouldNot beNull()
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

/**
 * Create a [ReportDefinition] for the given [pluginId] with the given parameters. Return a [Pair] with the
 * [reportName] and the definition which can be added to the map of definitions in a [ReporterConfig].
 */
private fun createReportDefinition(
    pluginId: String,
    reportName: String = pluginId,
    assetFiles: List<ReporterAsset> = emptyList(),
    assetDirectories: List<ReporterAsset> = emptyList(),
    nameMapping: ReportNameMapping? = null
): Pair<String, ReportDefinition> =
    reportName to ReportDefinition(
        pluginId = pluginId,
        assetFiles = assetFiles,
        assetDirectories = assetDirectories,
        nameMapping = nameMapping
    )

/**
 * Create a [ReporterConfig] based on the given parameters.
 */
private fun createReporterConfig(
    howToFixTextProviderFile: String = ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME,
    customLicenseTextDir: String? = null,
    vararg reportDefinitions: Pair<String, ReportDefinition>
): ReporterConfig =
    ReporterConfig(
        reportDefinitionsMap = reportDefinitions.toMap(),
        howToFixTextProviderFile = howToFixTextProviderFile,
        customLicenseTextDir = customLicenseTextDir
    )

/**
 * Create a mock for the [AdminConfigService] that is prepared to load the [AdminConfig] for the current context
 * and returns an object with the given [reporterConfig].
 */
private fun createAdminConfigService(reporterConfig: ReporterConfig): AdminConfigService =
    mockk {
        every {
            loadAdminConfig(configurationContext, ORGANIZATION_ID)
        } returns AdminConfig(
            reporterConfig = reporterConfig,
            ruleSets = mapOf(RULE_SET_NAME to ruleSet)
        )
    }

/**
 * Simulate downloading of the configuration file with the given [name].
 */
private fun simulateGetConfigFile(name: String): InputStream =
    when (name) {
        ruleSet.copyrightGarbageFile -> copyrightGarbage.toStream()
        ruleSet.licenseClassificationsFile -> licenseClassifications.toStream()
        ruleSet.resolutionsFile -> resolutions.toStream()
        else -> throw ConfigException("Unsupported config file: $name")
    }

/**
 * Return an [InputStream] with the serialized content of this object.
 */
private fun <T> T.toStream(): InputStream {
    val serialized = yamlMapper.writeValueAsBytes(this)
    return ByteArrayInputStream(serialized)
}

/** Get the private providers from a [CompositeLicenseFactProvider]. */
private fun CompositeLicenseFactProvider.getProviders(): List<LicenseFactProvider> {
    val providersProperty =
        CompositeLicenseFactProvider::class.declaredMemberProperties.first { it.name == "providers" }
    providersProperty.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return providersProperty.get(this) as List<LicenseFactProvider>
}
