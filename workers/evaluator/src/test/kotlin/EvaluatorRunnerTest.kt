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

package org.ossreviewtoolkit.server.workers.evaluator

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import java.io.File

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.server.config.ConfigException
import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration

const val SCRIPT_FILE = "/example.rules.kts"
private const val LICENSE_CLASSIFICATIONS_FILE = "/license-classifications.yml"
private const val UNKNOWN_RULES_KTS = "unknown.rules.kts"

class EvaluatorRunnerTest : WordSpec({
    val runner = EvaluatorRunner(createConfigManager())

    "run" should {
        "return an EvaluatorRun with one rule violation" {
            val result = runner.run(
                OrtResult.EMPTY,
                EvaluatorJobConfiguration(ruleSet = SCRIPT_FILE, licenseClassification = LICENSE_CLASSIFICATIONS_FILE)
            )
            val expectedRuleViolation = RuleViolation(
                rule = "Example violation.",
                pkg = null,
                license = null,
                licenseSource = null,
                severity = Severity.ERROR,
                message = "This is an example RuleViolation for test cases.",
                howToFix = ""
            )

            result.violations shouldBe listOf(expectedRuleViolation)
        }

        "throw an exception when no rule set is provided" {
            shouldThrow<IllegalArgumentException> {
                runner.run(OrtResult.EMPTY, EvaluatorJobConfiguration())
            }
        }

        "throw an exception if script file could not be found" {
            shouldThrow<ConfigException> {
                runner.run(OrtResult.EMPTY, EvaluatorJobConfiguration(ruleSet = UNKNOWN_RULES_KTS))
            }
        }
    }
})

private fun createConfigManager(): ConfigManager {
    val configManager = mockk<ConfigManager> {
        every {
            getFileAsString(
                any(),
                Path(SCRIPT_FILE)
            )
        } returns String(File("src/test/resources/example.rules.kts").inputStream().readAllBytes())

        every {
            getFile(
                any(),
                Path(LICENSE_CLASSIFICATIONS_FILE)
            )
        } returns File("src/test/resources/license-classifications.yml").inputStream()

        every {
            getFileAsString(
                any(),
                Path(UNKNOWN_RULES_KTS)
            )
        } answers { callOriginal() }

        every {
            getFile(
                any(),
                Path(UNKNOWN_RULES_KTS)
            )
        } answers { callOriginal() }
    }

    return configManager
}
