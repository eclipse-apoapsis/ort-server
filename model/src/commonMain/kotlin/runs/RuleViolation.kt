/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.model.runs

import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution

/**
 * A data class describing a rule violation that occurred during an ORT run.
 */
data class RuleViolation(
    val rule: String,
    val packageId: Identifier?,
    val license: String?,
    val licenseSource: String?,
    val severity: Severity,
    val message: String,
    val howToFix: String,
    val resolutions: List<RuleViolationResolution> = emptyList()
)

/**
 * Filters to apply when querying for rule violations.
 */
data class RuleViolationFilters(
    /**
     * Filter to only return resolved rule violations. Null if both, resolved an unresolved rule violations should be
     * returned.
     */
    val resolved: Boolean? = null
)
