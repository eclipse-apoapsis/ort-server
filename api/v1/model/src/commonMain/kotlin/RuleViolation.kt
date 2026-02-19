/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.api.v1.model

import kotlinx.serialization.Serializable

@Serializable
data class RuleViolation(
    val rule: String,
    val id: Identifier? = null,
    val license: String?,
    /**
     * The license source for which the rule violation was triggered. This field exists only for reasons of backward
     * compatibility. The ORT data model now supports multiple license sources for a rule violation, which are stored
     * in the [licenseSources] property. This property is set to the first license source if available, otherwise null.
     */
    @Deprecated(
        "ORT now supports multiple license sources for a rule violation. This property contains only " +
        "the first license source. Use the 'licenseSources' property instead"
    )
    val licenseSource: LicenseSource?,
    /** The set of license sources for which the rule violation was triggered. */
    val licenseSources: Set<LicenseSource> = emptySet(),
    val severity: Severity,
    val message: String,
    val howToFix: String,
    val resolutions: List<RuleViolationResolution> = emptyList(),
    /**
     * The purl of the [Package] this rule violation stems from. Null if the rule violation comes from a [Project] or
     * elsewhere.
     */
    val purl: String? = null
)

/**
 * Filters to apply when querying for rule violations.
 */
@Serializable
data class RuleViolationFilters(
    /**
     * Filter to only return resolved rule violations. Null if both, resolved an unresolved rule violations should be
     * returned.
     */
    val resolved: Boolean? = null
)
