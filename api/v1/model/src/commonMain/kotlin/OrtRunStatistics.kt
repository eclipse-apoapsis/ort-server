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

/**
 * A response object for returning ORT run statistics.
 */
@Serializable
data class OrtRunStatistics(
    /**
     * The number of issues found in the ORT run, or null if no jobs have finished yet.
     */
    val issuesCount: Long? = null,
    /**
     * The number of packages in the ORT run, or null if the analyzer job has not finished yet or has failed.
     */
    val packagesCount: Long? = null,
    /**
     * The list of unique ecosystems for the packages in the ORT run, or null if the analyzer job has not finished yet
     * or has failed.
     */
    val ecosystems: List<EcosystemStats>? = null,
    /**
     * The number of vulnerabilities found in the ORT run, or null if the advisor job has not finished yet or has
     * failed or was not configured to run.
     */
    val vulnerabilitiesCount: Long? = null,
    /**
     * The number of rule violations found in the ORT run, or null if the evaluator job has not finished yet or has
     * failed or was not configured to run.
     */
    val ruleViolationsCount: Long? = null,
)
