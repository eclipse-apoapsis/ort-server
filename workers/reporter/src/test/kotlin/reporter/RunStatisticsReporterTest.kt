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

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.maps.containAnyKeys
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf

import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.Statistics
import org.ossreviewtoolkit.server.workers.common.OrtTestData

class RunStatisticsReporterTest : WordSpec({
    "The RunStatisticsReporter" should {
        "create a correct run statistics file" {
            val reporter = RunStatisticsReporter()
            val ortResult = OrtTestData.result

            val input = ReporterInput(ortResult = ortResult)

            val reportFiles = reporter.generateReport(input, tempdir())
            reportFiles should haveSize(1)

            val runStatisticsFile = reportFiles.single()
            runStatisticsFile.name shouldBe "run-statistics.json"
            val actualResult = runStatisticsFile.readValue<Statistics>()
            actualResult shouldBe input.statistics
        }

        "be found by the service loader" {
            val reporter = RunStatisticsReporter()

            Reporter.ALL should containAnyKeys(reporter.type)
            Reporter.ALL[reporter.type] should beInstanceOf<RunStatisticsReporter>()
        }
    }
})
