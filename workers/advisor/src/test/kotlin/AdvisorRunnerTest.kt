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
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify

import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.advisor.Advisor
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.server.model.AdvisorJobConfiguration
import org.ossreviewtoolkit.server.model.PluginConfiguration

class AdvisorRunnerTest : WordSpec({
    val runner = AdvisorRunner()

    afterEach { unmockkAll() }

    "run" should {
        "return a valid AdvisorRun" {
            val factory = mockAdviceProviderFactory("VulnerableCode")
            mockAdvisorAll(listOf(factory))

            val run = runner.run(emptySet(), AdvisorJobConfiguration())

            run.config shouldBe AdvisorConfiguration()
        }

        "create the configured advice providers with the correct options and secrets" {
            val osvFactory = mockAdviceProviderFactory("OSV")
            val vulnerableCodeFactory = mockAdviceProviderFactory("VulnerableCode")
            mockAdvisorAll(listOf(osvFactory, vulnerableCodeFactory))

            val osvConfig = PluginConfiguration(
                options = mapOf("option1" to "value1", "option2" to "value2"),
                secrets = mapOf("secret1" to "pass1", "secret2" to "pass2")
            )

            val vulnerableCodeConfig = PluginConfiguration(
                options = mapOf("option3" to "value3", "option4" to "value4"),
                secrets = mapOf("secret3" to "pass3", "secret4" to "pass4")
            )

            val jobConfig = AdvisorJobConfiguration(
                advisors = listOf("OSV", "VulnerableCode"),
                config = mapOf(
                    "OSV" to osvConfig,
                    "VulnerableCode" to vulnerableCodeConfig
                )
            )

            runner.run(setOf(Package.EMPTY), jobConfig)

            verify(exactly = 1) {
               osvFactory.create(osvConfig.options, osvConfig.secrets)
               vulnerableCodeFactory.create(vulnerableCodeConfig.options, vulnerableCodeConfig.secrets)
            }
        }
    }
})

private fun mockAdviceProviderFactory(adviceProviderName: String) =
    mockk<AdviceProviderFactory<*>> {
        every { type } returns adviceProviderName

        every { create(any(), any()) } returns mockk<AdviceProvider> {
            every { providerName } returns adviceProviderName
            coEvery { retrievePackageFindings(any()) } returns emptyMap()
        }
    }

private fun mockAdvisorAll(adviceProviders: List<AdviceProviderFactory<*>>) {
    mockkObject(Advisor)
    mockk<Advisor> {
        every { Advisor.ALL } returns adviceProviders.associateByTo(sortedMapOf()) { it.type }
    }
}
