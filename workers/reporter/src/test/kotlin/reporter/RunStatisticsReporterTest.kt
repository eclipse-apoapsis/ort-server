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

import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.Statistics

class RunStatisticsReporterTest : WordSpec({
    "The RunStatisticsReporter" should {
        "create a correct run statistics file" {
            val reporter = RunStatisticsReporter()
            val ortResult = OrtTestData.result

            val input = ReporterInput(ortResult = ortResult)

            val reportFileResults = reporter.generateReport(input, tempdir())

            reportFileResults.shouldBeSingleton {
                it shouldBeSuccess { runStatisticsFile ->
                    runStatisticsFile.name shouldBe "statistics.json"
                    val actualResult = runStatisticsFile.readValue<Statistics>()
                    actualResult shouldBe input.statistics
                }
            }
        }

        "be found by the service loader" {
            val reporter = RunStatisticsReporter()

            Reporter.ALL should containAnyKeys(reporter.type)
            Reporter.ALL[reporter.type] should beInstanceOf<RunStatisticsReporter>()
        }
    }
})
