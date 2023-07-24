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

import java.io.FileNotFoundException

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration

const val SCRIPT_FILE = "/example.rules.kts"

class EvaluatorRunnerTest : WordSpec({
    val runner = EvaluatorRunner()

    "run" should {
        "return an EvaluatorRun with one rule violation" {
            val result = runner.run(OrtResult.EMPTY, EvaluatorJobConfiguration(ruleSet = SCRIPT_FILE))
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
            shouldThrow<FileNotFoundException> {
                runner.run(OrtResult.EMPTY, EvaluatorJobConfiguration(ruleSet = "unknown.rules.kts"))
            }
        }
    }
})
