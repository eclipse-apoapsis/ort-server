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

import java.io.FileNotFoundException

import org.ossreviewtoolkit.evaluator.Evaluator
import org.ossreviewtoolkit.model.EvaluatorRun
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration

class EvaluatorRunner {
    fun run(ortResult: OrtResult, config: EvaluatorJobConfiguration): EvaluatorRun {
        requireNotNull(config.ruleSet) {
            "No rule set is provided."
        }

        // TODO: Only rules script files, which are on the classpath can be used as rulesSet. Therefore, it only works
        //       with the OSADL rules set. The absolute path of the script file has to be configured in the job
        //       configuration so that it can be found on the classpath. In the future it has to be possible to apply
        //       other rules.
        val scriptUrl = config.ruleSet?.let { javaClass.getResource(it) }
            ?: throw FileNotFoundException("Could not find rule set '${config.ruleSet}'.")
        val script = scriptUrl.readText()

        // TODO: Currently, there is no concept for configuring and applying resolutions and license classifications.
        val evaluator = Evaluator(ortResult)

        return evaluator.run(script)
    }
}
