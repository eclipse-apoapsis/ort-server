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

import org.ossreviewtoolkit.advisor.Advisor
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.server.model.AdvisorJobConfiguration

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AdvisorRunner::class.java)

class AdvisorRunner {
    fun run(ortResult: OrtResult, config: AdvisorJobConfiguration): OrtResult {
        requireNotNull(ortResult.analyzer) {
            "The advisor cannot run due to the missing analyzer result in the provided ORT result."
        }

        // TODO: Find a way to make the AdvisorConfiguration configurable, otherwise the Advisor
        //       will run without any AdvisorProvider due to the missing correspondent configuration.
        val advisorConfig = AdvisorConfiguration()

        val advisorProviders = config.advisors.partition { Advisor.ALL.containsKey(it) }.let { (known, unknown) ->
            if (unknown.isNotEmpty()) {
                logger.warn(
                    """
                        The following advisors are unknown:
                            ${unknown.joinToString()}
                    """.trimIndent()
                )
            }

            if (known.isNotEmpty()) {
                logger.info(
                    """
                        The following advisors are activated:
                            ${known.joinToString()}
                    """.trimIndent()
                )
            }

            known.mapNotNull(Advisor.ALL::get).distinct()
        }

        val advisor = Advisor(advisorProviders, advisorConfig)
        val newOrtResult = advisor.retrieveFindings(ortResult)

        with(newOrtResult.getVulnerabilities(omitExcluded = true)) {
            val totalPackageCount = newOrtResult.getPackages(omitExcluded = true).size
            val vulnerabilityCount = values.sumOf { it.size }

            logger.info(
                "$size of $totalPackageCount package(s) (not counting excluded ones) are vulnerable, with " +
                    "$vulnerabilityCount vulnerabilities in total."
            )
        }

        return newOrtResult
    }
}
