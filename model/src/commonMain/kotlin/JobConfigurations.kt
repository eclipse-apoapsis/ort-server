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

package org.ossreviewtoolkit.server.model

import kotlinx.serialization.Serializable

/**
 * The configurations for the jobs in an [OrtRun].
 */
@Serializable
data class JobConfigurations(
    val analyzer: AnalyzerJobConfiguration = AnalyzerJobConfiguration(),
    val advisor: AdvisorJobConfiguration? = null,
    val scanner: ScannerJobConfiguration? = null,
    val evaluator: EvaluatorJobConfiguration? = null,
    val reporter: ReporterJobConfiguration? = null
)

/**
 * The configuration for an analyzer job.
 */
@Serializable
data class AnalyzerJobConfiguration(
    val allowDynamicVersions: Boolean = false,

    /** The explicit environment configuration to be used for this run. */
    val environmentConfig: EnvironmentConfig? = null
)

/**
 * The configuration for an advisor job.
 */
@Serializable
data class AdvisorJobConfiguration(
    /**
     * The Advisors to use (e.g. NexusIQ, VulnerableCode, DefectDB).
     */
    val advisors: List<String> = emptyList()
)

/**
 * The configuration for a scanner job.
 */
@Serializable
data class ScannerJobConfiguration(
    /**
     * Do not scan excluded projects or packages.
     */
    val skipExcluded: Boolean = false
)

/**
 * The configuration for an evaluator job.
 */
@Serializable
data class EvaluatorJobConfiguration(
    /**
     * The id of the rule set to use for the evaluation.
     */
    val ruleSet: String? = null
)

@Serializable
data class ReporterJobConfiguration(
    /**
     * The report formats to generate.
     */
    val formats: List<String> = emptyList()
)
