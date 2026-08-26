/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.config

import org.eclipse.apoapsis.ortserver.components.adminconfig.AdminConfig
import org.eclipse.apoapsis.ortserver.components.adminconfig.ReporterConfig
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.Issue

import org.slf4j.LoggerFactory

/**
 * A class that validates an [AdminConfig] and if the [JobConfigurations] of a run are compatible with it, or if they
 * reference entities like rule sets or assets that are not defined by the [AdminConfig].
 */
object AdminConfigValidator {
    private val logger = LoggerFactory.getLogger(AdminConfigValidator::class.java)

    /**
     * Validate that the [adminConfig] and that the [jobConfigs] are compatible with it. Return a list of found issues.
     * If the list is empty, the configurations are valid.
     */
    fun validate(adminConfig: AdminConfig, jobConfigs: JobConfigurations): List<Issue> = buildList {
        jobConfigs.ruleSet?.also { ruleSet ->
            if (ruleSet !in adminConfig.ruleSetNames) {
                add(
                    "Invalid rule set '$ruleSet'. Available rule sets are: ${adminConfig.ruleSetNames.joinToString()}."
                )
            }
        }

        jobConfigs.reporter?.also {
            addAll(validateReporterConfig(adminConfig.reporterConfig, it))
        }
    }.map { message ->
        createIssue(message, PARAMETER_VALIDATION_SOURCE)
    }.also { issues ->
        if (issues.isNotEmpty()) {
            logger.error(
                "Found ${issues.size} issues during admin config validation:\n" +
                        issues.joinToString(separator = "\n")
            )
        }
    }

    /**
     * Validate that the report formats and asset references in [jobConfig] are defined in [reporterConfig] and return a
     * list of found issues. If the list is empty, the configurations are valid.
     */
    private fun validateReporterConfig(
        reporterConfig: ReporterConfig,
        jobConfig: ReporterJobConfiguration
    ) = buildList {
        jobConfig.formats
            .filter { reporterConfig.getReportDefinition(it) == null }
            .forEach { format ->
                add(
                    "Invalid reporter format '$format' in reporter job configuration. " +
                            "Available formats are: ${reporterConfig.reportDefinitionNames.joinToString()}."
                )
            }

        jobConfig.assetFilesGroups
            .filterNot { it in reporterConfig.globalAssets }
            .forEach { assetGroup ->
                add(
                    "Invalid reporter asset files group '$assetGroup' in reporter job configuration. " +
                            "Available asset groups are: ${reporterConfig.globalAssets.keys.joinToString()}."
                )
            }

        jobConfig.assetDirectoriesGroups
            .filterNot { it in reporterConfig.globalAssets }
            .forEach { assetGroup ->
                add(
                    "Invalid reporter asset directories group '$assetGroup' in reporter job configuration. " +
                            "Available asset groups are: ${reporterConfig.globalAssets.keys.joinToString()}."
                )
            }
    }
}
