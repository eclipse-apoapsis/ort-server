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
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.maps.shouldMatchAll
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

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.model.Options
import org.ossreviewtoolkit.server.model.ReporterJobConfiguration
import org.ossreviewtoolkit.server.workers.common.OptionsTransformerFactory
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext
import org.ossreviewtoolkit.server.workers.common.context.WorkerContextFactory

private const val RUN_ID = 20230522093727L

class ReporterRunnerTest : WordSpec({
    afterEach {
        unmockkObject(Reporter)
    }

    "run" should {
        "return a map with report format and directory" {
            val storage = mockk<ReportStorage>()
            every { storage.storeReportFiles(any(), any()) } just runs
            val (contextFactory, _) = mockContext()
            val runner = ReporterRunner(storage, contextFactory, OptionsTransformerFactory(), mockk())

            val result = runner.run(RUN_ID, OrtResult.EMPTY, ReporterJobConfiguration(listOf("WebApp")))

            result.shouldMatchAll(
                "WebApp" to { it.size shouldBe 1 }
            )

            verify {
                storage.storeReportFiles(RUN_ID, result.getValue("WebApp"))
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

            mockkObject(Reporter)
            every { Reporter.ALL } returns sortedMapOf(
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
            coEvery { context.downloadConfigurationFiles(any()) } answers {
                val paths = firstArg<Collection<Path>>()
                paths.associateWith { File("$resolvedTemplatePrefix${it.path}") }
            }

            val runner = ReporterRunner(mockk(relaxed = true), contextFactory, OptionsTransformerFactory(), mockk())
            runner.run(RUN_ID, OrtResult.EMPTY, jobConfig)

            val slotPlainOptions = slot<Options>()
            val slotTemplateOptions = slot<Options>()
            verify {
                plainReporter.generateReport(any(), any(), capture(slotPlainOptions))
                templateReporter.generateReport(any(), any(), capture(slotTemplateOptions))

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

            mockkObject(Reporter)
            every { Reporter.ALL } returns sortedMapOf(
                templateFormat to templateReporter
            )

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
            coEvery { context.downloadConfigurationFiles(any()) } answers {
                val paths = firstArg<Collection<Path>>()
                paths.associateWith { File("$resolvedTemplatePrefix${it.path}") }
            }

            val runner = ReporterRunner(mockk(relaxed = true), contextFactory, OptionsTransformerFactory(), mockk())
            runner.run(RUN_ID, OrtResult.EMPTY, jobConfig)

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

            mockkObject(Reporter)
            mockk<Reporter> {
                every { Reporter.ALL } returns sortedMapOf(
                    reportFormat to mockk {
                        every { type } returns reportFormat
                        every { generateReport(any(), any(), any()) } throws
                                FileNotFoundException("Something went wrong...")
                    }
                )
            }

            val (contextFactory, _) = mockContext()

            val exception = shouldThrow<IllegalArgumentException> {
                val runner = ReporterRunner(mockk(relaxed = true), contextFactory, OptionsTransformerFactory(), mockk())

                runner.run(RUN_ID, OrtResult.EMPTY, ReporterJobConfiguration(listOf(reportFormat)))
            }

            exception.message shouldContain "TestFormat: .*FileNotFoundException = Something went wrong".toRegex()
        }

        "should throw an exception when requesting an unknown report format" {
            val (contextFactory, _) = mockContext()
            val runner = ReporterRunner(mockk(relaxed = true), contextFactory, OptionsTransformerFactory(), mockk())

            shouldThrow<IllegalArgumentException> {
                runner.run(RUN_ID, OrtResult.EMPTY, ReporterJobConfiguration(listOf("UnknownFormat")))
            }
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
 * Return a pair of a mock context factory and a mock context. The factory is prepared to return the context.
 */
private fun mockContext(): Pair<WorkerContextFactory, WorkerContext> {
    val context = mockk<WorkerContext> {
        every { close() } just runs
    }
    val factory = mockk<WorkerContextFactory> {
        every { createContext(RUN_ID) } returns context
    }

    return factory to context
}
