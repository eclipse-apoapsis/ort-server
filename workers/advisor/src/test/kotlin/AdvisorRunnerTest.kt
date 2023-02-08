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

package org.ossreviewtoolkit.server.workers.advisor

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

import org.ossreviewtoolkit.advisor.Advisor
import org.ossreviewtoolkit.model.AdvisorRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.server.model.AdvisorJobConfiguration

class AdvisorRunnerTest : WordSpec({
    val configurator = mockk<AdvisorConfigurator>()
    val runner = AdvisorRunner(configurator)

    "run" should {
        "invoke the advisor created by the configurator" {
            val packages = setOf(Package.EMPTY.copy(id = Identifier("type", "namespace", "name", "version")))
            val config = AdvisorJobConfiguration(listOf("TestAdviceProvider"))

            val advisor = mockk<Advisor>()
            val advisorRun = mockk<AdvisorRun>()
            every { configurator.createAdvisor(config.advisors) } returns advisor
            coEvery { advisor.advise(packages) } returns advisorRun

            val actualAdvisorRun = runner.run(packages, config)

            actualAdvisorRun shouldBe advisorRun
        }
    }
})
