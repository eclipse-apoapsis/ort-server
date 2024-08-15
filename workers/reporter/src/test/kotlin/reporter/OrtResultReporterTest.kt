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
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.maps.containAnyKeys
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf

import org.eclipse.apoapsis.ortserver.workers.common.OrtTestData

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.unpack

class OrtResultReporterTest : WordSpec({
    "The OrtResultReporter" should {
        "create a correct ORT result file" {
            val reporter = OrtResultReporter()

            // Set the options of the advisor configuration to null because the configured empty map will be serialized
            // as null. Changing the value in OrtTestData is not possible, because the options in the server model are
            // not nullable and changing it would therefore make other tests fail.
            val advisorRun = OrtTestData.advisorRun.copy(
                config = OrtTestData.advisorConfiguration.copy(config = null)
            )
            val ortResult = OrtTestData.result.copy(advisor = advisorRun)

            // Remove the secrets from the scanner configuration as they will not be serialized.
            val scannerRun = OrtTestData.scannerRun.copy(
                config = OrtTestData.scannerConfiguration.copy(
                    config = OrtTestData.scannerConfiguration.config?.mapValues { it.value.copy(secrets = emptyMap()) }
                )
            )
            val expectedOrtResult = ortResult.copy(scanner = scannerRun)

            val input = ReporterInput(ortResult = ortResult)

            val reportFileResults = reporter.generateReport(
                input,
                tempdir(),
                PluginConfiguration(options = mapOf(OrtResultReporter.COMPRESSED_PROPERTY to "false"))
            )

            reportFileResults.shouldBeSingleton {
                it shouldBeSuccess { reportFile ->
                    reportFile.name shouldBe "ort-result.yml"
                    val actualResult = reportFile.readValue<OrtResult>()
                    actualResult shouldBe expectedOrtResult
                }
            }
        }

        "create a compressed ORT result file" {
            val reporter = OrtResultReporter()

            val advisorRun = OrtTestData.advisorRun.copy(
                config = OrtTestData.advisorConfiguration.copy(config = null)
            )
            val ortResult = OrtTestData.result.copy(advisor = advisorRun)

            // Remove the secrets from the scanner configuration as they will not be serialized.
            val scannerRun = OrtTestData.scannerRun.copy(
                config = OrtTestData.scannerConfiguration.copy(
                    config = OrtTestData.scannerConfiguration.config?.mapValues { it.value.copy(secrets = emptyMap()) }
                )
            )
            val expectedOrtResult = ortResult.copy(scanner = scannerRun)

            val input = ReporterInput(ortResult = ortResult)

            val outputDir = tempdir()
            val reportFileResults = reporter.generateReport(input, outputDir)

            reportFileResults.shouldBeSingleton {
                it shouldBeSuccess { archiveFile ->
                    archiveFile.unpack(outputDir)
                    val ortResultFile = outputDir.resolve("ort-result.yml")
                    val actualResult = ortResultFile.readValue<OrtResult>()
                    actualResult shouldBe expectedOrtResult
                }
            }
        }

        "be found by the service loader" {
            val reporter = OrtResultReporter()

            Reporter.ALL should containAnyKeys(reporter.type)
            Reporter.ALL[reporter.type] should beInstanceOf<OrtResultReporter>()
        }
    }
})
