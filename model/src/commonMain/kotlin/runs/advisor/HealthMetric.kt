/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.model.runs.advisor

/** A project health metric that an advisor reported for a package. */
data class HealthMetric(
    /** The unique, human-readable name of the metric. */
    val name: String,

    /** A summary of the documentation about the metric. */
    val documentation: String? = null,

    /** A link to the documentation about the metric. */
    val documentationLink: String? = null,

    /** The lowest value the metric can take, or null if it is unbounded or unknown. */
    val minValue: Double? = null,

    /** The highest value the metric can take, or null if it is unbounded or unknown. */
    val maxValue: Double? = null,

    /** Whether a higher value is better, or null if unknown. */
    val higherIsBetter: Boolean? = null,

    /** The value as reported by the advisor, or null if the advisor could not compute it. [reason] then says why. */
    val value: Double?,

    /** The value that [criticality] was rated against, or null if the rating does not use one. */
    val referenceValue: Double? = null,

    /** How critical the [value] is, or null if there is no [value]. */
    val criticality: Criticality? = null,

    /** The reason for the value, or why there is no value. */
    val reason: String? = null,

    /** Details about the findings behind the value. */
    val details: List<String> = emptyList()
)
