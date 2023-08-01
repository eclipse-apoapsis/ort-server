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

package org.ossreviewtoolkit.server.workers.config

import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.runs.OrtIssue

/**
 * An interface defining the validation result of the configuration of an ORT run.
 *
 * Validations can be either successful or fail. In case of a successful validation, resolved [JobConfigurations] are
 * available. There may still be issues (typically hints or warnings), but the run can continue. If a validation fails,
 * the result contains only issues; they should then provide the reason for the failure.
 */
sealed interface ConfigValidationResult {
    /** A (possibly empty) list with issues detected during validation. */
    val issues: List<OrtIssue>
}

/**
 * A data class representing a successful validation of a run configuration.
 */
data class ConfigValidationResultSuccess(
    /** The resolved [JobConfigurations] returned by the validation script. */
    val resolvedConfigurations: JobConfigurations,

    /** A list with issues that have been detected during validation. */
    override val issues: List<OrtIssue> = emptyList()
) : ConfigValidationResult

/**
 * A data class representing a failed validation of a run configuration.
 */
data class ConfigValidationResultFailure(
    /** A list with issues that were the cause of the failed validation. */
    override val issues: List<OrtIssue>
) : ConfigValidationResult
