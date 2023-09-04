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

import org.ossreviewtoolkit.evaluator.Evaluator
import org.ossreviewtoolkit.model.EvaluatorRun
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration

class EvaluatorRunner(
    /**
     * The config manager is used to download the rule script as well as the file describing license classifications.
     */
    private val configManager: ConfigManager
) {
    /**
     * The rule set script and the license classifications file are obtained from the [configManager] using the
     * respective paths specified in [config]. In case the path to the license classifications file is not provided,
     * an empty [LicenseClassifications] is passed to the Evaluator.
     */
    fun run(ortResult: OrtResult, config: EvaluatorJobConfiguration): EvaluatorRun {
        val script = config.ruleSet?.let { configManager.getFileAsString(null, Path(it)) }
            ?: throw IllegalArgumentException("The rule set path is not specified in the config.", null)

        val licenseClassifications = config.licenseClassification?.let {
            configManager.getFile(null, Path(it)).use { rawLicenseClassifications ->
                yamlMapper.readValue(rawLicenseClassifications, LicenseClassifications::class.java)
            }
        } ?: LicenseClassifications()

        val evaluator = Evaluator(
            ortResult = ortResult,
            licenseClassifications = licenseClassifications
        )

        return evaluator.run(script)
    }
}
