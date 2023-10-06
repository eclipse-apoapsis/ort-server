/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.reporter

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldMatchAll
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify

import java.io.File
import java.io.FileNotFoundException

import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant

import org.ossreviewtoolkit.model.EvaluatorRun
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.server.config.ConfigException
import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Context
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration
import org.ossreviewtoolkit.server.model.Options
import org.ossreviewtoolkit.server.model.ReporterAsset
import org.ossreviewtoolkit.server.model.ReporterJobConfiguration
import org.ossreviewtoolkit.server.workers.common.OptionsTransformerFactory
import org.ossreviewtoolkit.server.workers.common.OrtTestData
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext
import org.ossreviewtoolkit.server.workers.common.context.WorkerContextFactory
import org.ossreviewtoolkit.server.workers.common.resolvedConfigurationContext

private const val RUN_ID = 20230522093727L
private val configurationContext = Context("theConfigContext")

class ReporterRunnerTest : WordSpec({
    afterEach {
        unmockkObject(Reporter)
    }

    val configManager = mockk<ConfigManager> {
        every { getFile(any(), any()) } throws ConfigException("", null)
    }

    val configDirectory = tempdir()
    val outputDirectory = tempdir()

    /**
     * Return a pair of a mock context factory and a mock context. The factory is prepared to return the context. The
     * context mock is prepared to create a temporary directory.
     */
    fun mockContext(): Pair<WorkerContextFactory, WorkerContext> {
        val context = mockk<WorkerContext> {
            every { resolvedConfigurationContext } returns configurationContext
            every { createTempDir() } returnsMany listOf(configDirectory, outputDirectory)
            every { close() } just runs
        }
        val factory = mockk<WorkerContextFactory> {
            every { createContext(RUN_ID) } returns context
        }

        return factory to context
    }

    "run" should {
        "return a map with report format and directory" {
            val storage = mockk<ReportStorage>()
            every { storage.storeReportFiles(any(), any()) } just runs
            val (contextFactory, _) = mockContext()
            val runner = ReporterRunner(storage, contextFactory, OptionsTransformerFactory(), configManager, mockk())

            val result = runner.run(RUN_ID, OrtResult.EMPTY, ReporterJobConfiguration(formats = listOf("WebApp")), null)

            result.reports.shouldMatchAll(
                "WebApp" to { it.size shouldBe 1 }
            )

            verify {
                storage.storeReportFiles(RUN_ID, result.reports.getValue("WebApp"))
            }
        }

        "resolve template file references" {
            val templateFileReference1 = "reporter/my-template.ftl"
            val templateFileReference2 = "reporter/my-other-template.ftl"
            val templateFileReference3 = "reporter/another-template.ftl"
            val resolvedTemplatePrefix = "/var/tmp/"

            val plainFormat = "plain"
            val plainReporter = reporterMock(plainFormat)
            every { plainReporter.generateReport(any(), any(), any()) } returns listOf(tempfile())

            val templateFormat = "template"
            val templateReporter = reporterMock(templateFormat)
            every { templateReporter.generateReport(any(), any(), any()) } returns listOf(tempfile())

            mockReportersAll(
                plainFormat to plainReporter,
                templateFormat to templateReporter
            )

            val plainOptions = mapOf("pretty" to "true")
            val jobConfig = ReporterJobConfiguration(
                formats = listOf(plainFormat, templateFormat),
                options = mapOf(
                    plainFormat to plainOptions,
                    templateFormat to mapOf(
                        "ugly" to "false",
                        "templateFile" to "${ReporterComponent.TEMPLATE_REFERENCE}$templateFileReference1",
                        "otherTemplate" to "${ReporterComponent.TEMPLATE_REFERENCE}$templateFileReference2, " +
                                "${ReporterComponent.TEMPLATE_REFERENCE}$templateFileReference3"
                    )
                )
            )

            val (contextFactory, context) = mockContext()
            coEvery { context.downloadConfigurationFiles(any(), configDirectory) } answers {
                val paths = firstArg<Collection<Path>>()
                paths.associateWith { File("$resolvedTemplatePrefix${it.path}") }
            }

            val runner = ReporterRunner(
                mockk(relaxed = true),
                contextFactory,
                OptionsTransformerFactory(),
                configManager,
                mockk()
            )
            runner.run(RUN_ID, OrtResult.EMPTY, jobConfig, null)

            val slotPlainOptions = slot<Options>()
            val slotTemplateOptions = slot<Options>()
            verify {
                plainReporter.generateReport(any(), outputDirectory, capture(slotPlainOptions))
                templateReporter.generateReport(any(), outputDirectory, capture(slotTemplateOptions))

                context.close()
            }

            val expectedTemplateOptions = mapOf(
                "ugly" to "false",
                "templateFile" to "$resolvedTemplatePrefix$templateFileReference1",
                "otherTemplate" to "$resolvedTemplatePrefix$templateFileReference2," +
                        "$resolvedTemplatePrefix$templateFileReference3"
            )
            slotPlainOptions.captured shouldBe plainOptions
            slotTemplateOptions.captured shouldBe expectedTemplateOptions
        }

        "handle template file references and other references" {
            val fileReference = "reporter/my-template.ftl"
            val otherReference1 = "foo.ftl"
            val otherReference2 = "bar.ftl"
            val resolvedTemplatePrefix = "/var/tmp/"

            val templateFormat = "template"
            val templateReporter = reporterMock(templateFormat)
            every { templateReporter.generateReport(any(), any(), any()) } returns listOf(tempfile())

            mockReportersAll(templateFormat to templateReporter)

            val jobConfig = ReporterJobConfiguration(
                formats = listOf(templateFormat),
                options = mapOf(
                    templateFormat to mapOf(
                        "templateFile" to "$otherReference1,${ReporterComponent.TEMPLATE_REFERENCE}$fileReference," +
                                otherReference2
                    )
                )
            )

            val (contextFactory, context) = mockContext()
            coEvery { context.downloadConfigurationFiles(any(), configDirectory) } answers {
                val paths = firstArg<Collection<Path>>()
                paths.associateWith { File("$resolvedTemplatePrefix${it.path}") }
            }

            val runner = ReporterRunner(
                mockk(relaxed = true),
                contextFactory,
                OptionsTransformerFactory(),
                configManager,
                mockk()
            )
            runner.run(RUN_ID, OrtResult.EMPTY, jobConfig, null)

            val slotOptions = slot<Options>()
            verify {
                templateReporter.generateReport(any(), any(), capture(slotOptions))

                context.close()
            }

            val expectedTemplateOptions = mapOf(
                "templateFile" to "$otherReference1,$resolvedTemplatePrefix$fileReference,$otherReference2"
            )
            slotOptions.captured shouldBe expectedTemplateOptions
        }

        "should throw an exception when a reporter fails" {
            val reportFormat = "TestFormat"

            val reporter = mockk<Reporter> {
                every { type } returns reportFormat
                every { generateReport(any(), any(), any()) } throws
                        FileNotFoundException("Something went wrong...")
            }
            mockReportersAll(reportFormat to reporter)

            val (contextFactory, _) = mockContext()

            val exception = shouldThrow<IllegalArgumentException> {
                val runner = ReporterRunner(
                    mockk(relaxed = true),
                    contextFactory,
                    OptionsTransformerFactory(),
                    configManager,
                    mockk()
                )

                runner.run(RUN_ID, OrtResult.EMPTY, ReporterJobConfiguration(formats = listOf(reportFormat)), null)
            }

            exception.message shouldContain "TestFormat: .*FileNotFoundException = Something went wrong".toRegex()
        }

        "should throw an exception when requesting an unknown report format" {
            val (contextFactory, _) = mockContext()
            val runner = ReporterRunner(
                mockk(relaxed = true),
                contextFactory,
                OptionsTransformerFactory(),
                configManager,
                mockk()
            )

            shouldThrow<IllegalArgumentException> {
                runner.run(RUN_ID, OrtResult.EMPTY, ReporterJobConfiguration(formats = listOf("UnknownFormat")), null)
            }
        }

        "use the package configurations resolved by the evaluator" {
            val (contextFactory, _) = mockContext()
            val runner = ReporterRunner(
                mockk(relaxed = true),
                contextFactory,
                OptionsTransformerFactory(),
                configManager,
                mockk()
            )

            val format = "format"
            val reporter = reporterMock(format)
            val reporterInputSlot = slot<ReporterInput>()
            every { reporter.generateReport(capture(reporterInputSlot), any(), any()) } returns emptyList()

            mockReportersAll(format to reporter)

            val result = runner.run(
                runId = RUN_ID,
                ortResult = OrtTestData.result,
                config = ReporterJobConfiguration(formats = listOf(format)),
                evaluatorConfig = EvaluatorJobConfiguration()
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
            val (contextFactory, _) = mockContext()
            val runner = ReporterRunner(
                mockk(relaxed = true),
                contextFactory,
                OptionsTransformerFactory(),
                configManager,
                mockk()
            )

            val format = "format"
            val reporter = reporterMock(format)
            every { reporter.generateReport(any(), any(), any()) } returns emptyList()

            mockReportersAll(format to reporter)

            val result = runner.run(
                runId = RUN_ID,
                ortResult = OrtTestData.result,
                config = ReporterJobConfiguration(formats = listOf(format)),
                evaluatorConfig = null
            )

            result.resolvedPackageConfigurations should
                    containExactlyInAnyOrder(OrtTestData.result.repository.config.packageConfigurations)
        }

        "use the resolutions resolved by the evaluator" {
            val (contextFactory, _) = mockContext()
            val runner = ReporterRunner(
                mockk(relaxed = true),
                contextFactory,
                OptionsTransformerFactory(),
                configManager,
                mockk()
            )

            val format = "format"
            val reporter = reporterMock(format)
            val reporterInputSlot = slot<ReporterInput>()
            every { reporter.generateReport(capture(reporterInputSlot), any(), any()) } returns emptyList()

            mockReportersAll(format to reporter)

            val ruleViolation = RuleViolation("RULE", null, null, null, Severity.ERROR, "message", "howToFix")

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
                evaluatorConfig = EvaluatorJobConfiguration()
            )

            result.resolvedResolutions should beNull()

            reporterInputSlot.isCaptured shouldBe true
            reporterInputSlot.captured.resolutionProvider.isResolved(OrtTestData.issue) shouldBe true
            reporterInputSlot.captured.resolutionProvider.isResolved(ruleViolation) shouldBe true
            reporterInputSlot.captured.resolutionProvider.isResolved(OrtTestData.vulnerability) shouldBe true
        }

        "resolve resolutions if no evaluator job is configured" {
            val (contextFactory, _) = mockContext()
            val runner = ReporterRunner(
                mockk(relaxed = true),
                contextFactory,
                OptionsTransformerFactory(),
                configManager,
                mockk()
            )

            val format = "format"
            val reporter = reporterMock(format)
            every { reporter.generateReport(any(), any(), any()) } returns emptyList()

            mockReportersAll(format to reporter)

            val result = runner.run(
                runId = RUN_ID,
                ortResult = OrtTestData.result.copy(
                    evaluator = EvaluatorRun(
                        startTime = Clock.System.now().toJavaInstant(),
                        endTime = Clock.System.now().toJavaInstant(),
                        violations = listOf(
                            RuleViolation("RULE", null, null, null, Severity.ERROR, "message", "howToFix")
                        )
                    )
                ),
                config = ReporterJobConfiguration(formats = listOf(format)),
                evaluatorConfig = null
            )

            result.resolvedResolutions shouldBe OrtTestData.result.repository.config.resolutions
        }

        "download asset files" {
            val format = "testAssetFiles"
            val reporter = reporterMock(format)
            every { reporter.generateReport(any(), any(), any()) } returns listOf(tempfile())

            mockReportersAll(format to reporter)

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
            val (contextFactory, context) = mockContext()
            coEvery { context.downloadConfigurationFile(any(), any(), any()) } answers {
                val path = firstArg<String>()
                val dir = secondArg<File>()
                val relativeDir = if (dir == configDirectory) {
                    null
                } else {
                    dir.parentFile shouldBe configDirectory
                    dir.isDirectory shouldBe true
                    dir.name
                }
                downloadedAssets += ReporterAsset(path, relativeDir, thirdArg())
                File(path)
            }

            val runner = ReporterRunner(
                mockk(relaxed = true),
                contextFactory,
                OptionsTransformerFactory(),
                configManager,
                mockk()
            )
            runner.run(RUN_ID, OrtResult.EMPTY, jobConfig, null)

            downloadedAssets shouldContainExactlyInAnyOrder assetFiles
        }

        "download asset directories" {
            val format = "testAssetDirectories"
            val reporter = reporterMock(format)
            every { reporter.generateReport(any(), any(), any()) } returns listOf(tempfile())

            mockReportersAll(format to reporter)

            val assetDirectories = listOf(
                ReporterAsset("data"),
                ReporterAsset("images", "imgs", "ignored")
            )
            val dataFiles = setOf(Path("data1.txt"), Path("data2.xml"))
            val imageFiles = setOf(Path("foo.png"), Path("bar.gif"), Path("baz.jpg"))
            val jobConfig = ReporterJobConfiguration(
                formats = listOf(format),
                assetDirectories = assetDirectories
            )

            val resolvedContext = Context("theResolvedContext")
            coEvery { configManager.listFiles(resolvedContext, Path("data")) } returns dataFiles
            coEvery { configManager.listFiles(resolvedContext, Path("images")) } returns imageFiles

            val downloadedAssets = mutableMapOf<Collection<Path>, File>()
            val (contextFactory, context) = mockContext()
            every { context.resolvedConfigurationContext } returns resolvedContext
            every { context.configManager } returns configManager
            coEvery { context.downloadConfigurationFiles(any(), any()) } answers {
                val paths = firstArg<Collection<Path>>()
                val dir = secondArg<File>()
                dir.isDirectory shouldBe true
                downloadedAssets[paths] = dir
                paths.associateWith { File(it.path) }
            }

            val runner = ReporterRunner(
                mockk(relaxed = true),
                contextFactory,
                OptionsTransformerFactory(),
                configManager,
                mockk()
            )
            runner.run(RUN_ID, OrtResult.EMPTY, jobConfig, null)

            downloadedAssets.keys shouldHaveSize 2
            downloadedAssets[dataFiles] shouldBe configDirectory

            val imageDir = downloadedAssets[imageFiles].shouldNotBeNull()
            imageDir.parentFile shouldBe configDirectory
            imageDir.name shouldBe "imgs"
        }
    }
})

/**
 * Create a mock [Reporter] that is prepared to return the given [reporterType].
 */
private fun reporterMock(reporterType: String): Reporter =
    mockk {
        every { type } returns reporterType
    }

/**
 * Mock the list of available reporters in the [Reporter] companion object to the given [reporters].
 */
private fun mockReportersAll(vararg reporters: Pair<String, Reporter>) {
    mockkObject(Reporter)
    every { Reporter.ALL } returns sortedMapOf(*reporters)
}
