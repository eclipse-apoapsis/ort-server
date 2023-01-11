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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull

import java.io.File
import java.net.URI

import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant

import org.ossreviewtoolkit.advisor.AbstractAdviceProviderFactory
import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.model.AdvisorCapability
import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AdvisorSummary
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.Vulnerability
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.server.model.AdvisorJobConfiguration
import org.ossreviewtoolkit.utils.common.enumSetOf

class AdvisorRunnerTest : WordSpec({
    val runner = AdvisorRunner()

    "run" should {
        "return an OrtResult with an empty AdvisorRun" {
            val ortResult = File("src/test/resources/ort-result.yml").readValue<OrtResult>()
            val config = AdvisorJobConfiguration()

            val newOrtResult = runner.run(ortResult, config)

            newOrtResult.advisor.shouldNotBeNull()
            newOrtResult.getAdvisorResults().shouldBeEmpty()
        }

        "throw an exception when no AdvisorRun was created" {
            shouldThrow<IllegalArgumentException> {
                runner.run(OrtResult(Repository(VcsInfo.EMPTY)), AdvisorJobConfiguration())
            }
        }

        "return an OrtResult with Advisor result" {
            val ortResult = File("src/test/resources/ort-result.yml").readValue<OrtResult>()
            val config = AdvisorJobConfiguration(listOf("TestAdviceProvider"))

            val newOrtResult = runner.run(ortResult, config)

            newOrtResult.advisor.shouldNotBeNull()
            TestAdviceProvider.storedPackages shouldContainExactlyInAnyOrder ortResult.getPackages(true)
                .map { it.metadata }
            newOrtResult.getAdvisorResults() shouldContainExactly TestAdviceProvider.RESULT
        }
    }
})

class TestAdviceProvider(name: String) : AdviceProvider(name) {
    class Factory : AbstractAdviceProviderFactory<TestAdviceProvider>("TestAdviceProvider") {
        override fun create(config: AdvisorConfiguration) = TestAdviceProvider("TestAdviceProvider")
    }

    companion object {
        var storedPackages = emptyList<Package>()
        val RESULT = mapOf(
            Identifier("type", "namespace", "name", "version") to listOf(
                AdvisorResult(
                    advisor = AdvisorDetails("Test", enumSetOf(AdvisorCapability.VULNERABILITIES)),
                    summary = AdvisorSummary(
                        Clock.System.now().toJavaInstant(),
                        Clock.System.now().toJavaInstant()
                    ),
                    defects = emptyList(),
                    vulnerabilities = listOf(
                        Vulnerability(
                            id = "TEST-CVE-1",
                            references = listOf(
                                VulnerabilityReference(URI.create("Test.File"), null, null)
                            )
                        )
                    )
                )
            )
        )
    }

    override val details = AdvisorDetails(providerName, enumSetOf(AdvisorCapability.VULNERABILITIES))

    override suspend fun retrievePackageFindings(packages: List<Package>): Map<Package, List<AdvisorResult>> {
        storedPackages = packages

        return RESULT.mapKeys { Package.EMPTY.copy(id = it.key) }
    }
}
