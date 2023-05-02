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
import io.kotest.matchers.maps.shouldMatchAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject

import java.io.FileNotFoundException

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.server.model.ReporterJobConfiguration

class ReporterRunnerTest : WordSpec({
    val runner = ReporterRunner()

    afterEach {
        unmockkObject(Reporter)
    }

    "run" should {
        "return a map with report format and directory" {
            val result = runner.run(OrtResult.EMPTY, ReporterJobConfiguration(listOf("WebApp")))

            result.shouldMatchAll(
                "WebApp" to { it.size shouldBe 1 }
            )
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

            val exception = shouldThrow<IllegalArgumentException> {
                runner.run(OrtResult.EMPTY, ReporterJobConfiguration(listOf(reportFormat)))
            }

            exception.message shouldContain "TestFormat: .*FileNotFoundException = Something went wrong".toRegex()
        }

        "should throw an exception when requesting an unknown report format" {
            shouldThrow<IllegalArgumentException> {
                runner.run(OrtResult.EMPTY, ReporterJobConfiguration(listOf("UnknownFormat")))
            }
        }
    }
})
