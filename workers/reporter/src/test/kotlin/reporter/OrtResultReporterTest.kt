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

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.server.workers.common.OrtTestData

class OrtResultReporterTest : WordSpec({
    "The OrtResultReporter" should {
        "create a correct ORT result file" {
            val reporter = OrtResultReporter()

            // Set the options of the advisor configuration to null because the configured empty map will be serialized
            // as null. Changing the value in OrtTestData is not possible, because the options in the server model are
            // not nullable and changing it would therefore make other tests fail.
            val advisorRun = OrtTestData.ortAdvisorRun.copy(
                config = OrtTestData.ortAdvisorConfiguration.copy(options = null)
            )
            val ortResult = OrtTestData.ortResult.copy(advisor = advisorRun)
            val input = ReporterInput(ortResult = ortResult)

            val reportFiles = reporter.generateReport(input, tempdir())
            reportFiles should haveSize(1)

            val ortResultFile = reportFiles.single()
            ortResultFile.name shouldBe "ort-result.yml"
            ortResultFile.readValue<OrtResult>() shouldBe ortResult
        }

        "be found by the service loader" {
            val reporter = OrtResultReporter()

            Reporter.ALL should containAnyKeys(reporter.type)
            Reporter.ALL[reporter.type] should beInstanceOf<OrtResultReporter>()
        }
    }
})
