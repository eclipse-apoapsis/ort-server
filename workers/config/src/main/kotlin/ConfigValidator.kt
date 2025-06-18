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

package org.eclipse.apoapsis.ortserver.workers.config

import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.scriptsInstancesSharing
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.services.config.AdminConfig
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.resolvedConfigurationContext

import org.ossreviewtoolkit.utils.scripting.ScriptRunner

import org.slf4j.LoggerFactory

/**
 * A class for validating and transforming the parameters of an ORT run using a validation script.
 *
 * An instance can be created for a specific [WorkerContext]. It can then be used to run a specific validation and
 * transformation script and obtain the results produced by this script.
 */
class ConfigValidator private constructor(
    /** The current [WorkerContext]. */
    private val context: WorkerContext,

    /** The service to access the admin configuration. */
    private val adminConfigService: AdminConfigService
) : ScriptRunner() {
    companion object {
        /**
         * Constant for the source of an issue that is generated if the validation script fails to compile.
         */
        const val INVALID_SCRIPT_SOURCE = "VALIDATION_SCRIPT_ERROR"

        /**
         * Constant for the source of an issue that is generated if parameters to trigger a run do not comply with
         * settings in the admin configuration.
         */
        const val ADMIN_CONFIG_VALIDATION_SOURCE = "PARAMETER_VALIDATION"

        private val logger = LoggerFactory.getLogger(ConfigValidator::class.java)

        /**
         * Return a new instance of [ConfigValidator] to validate the parameters of the ORT run stored in the given
         * [context] using the given [configService] to access the admin configuration.
         */
        fun create(context: WorkerContext, configService: AdminConfigService): ConfigValidator =
            ConfigValidator(context, configService)

        /**
         * Create a [ConfigValidationResult] for the case that there was an error during the execution of the given
         * [validation script][script] caused by the given [exception].
         */
        private fun createScriptErrorResult(script: String, exception: Throwable): ConfigValidationResult =
            ConfigValidationResultFailure(
                issues = listOf(
                    Issue(
                        Clock.System.now(),
                        INVALID_SCRIPT_SOURCE,
                        "Error when executing validation script. This is a problem with the configuration " +
                                "of ORT Server.",
                        Severity.ERROR
                    )
                )
            ).also {
                logger.error("Error when executing validation script.", exception)
                logger.debug("Content of the script:\n{}", script)
            }
    }

    override val compConfig = createJvmCompilationConfigurationFromTemplate<ValidationScriptTemplate>()

    override val evalConfig = ScriptEvaluationConfiguration {
        constructorArgs(context, Clock.System.now())
        scriptsInstancesSharing(true)
    }

    /**
     * Validate the parameters of the ORT run from the current context by executing the given [script]. Return the
     * result of this script. If the script execution fails, return a failure result as well with the information
     * available about the failure.
     */
    fun validate(script: String): ConfigValidationResult {
        return runCatching {
            val executedScript = runScript(script).scriptInstance as ValidationScriptTemplate

            when (val result = executedScript.validationResult) {
                is ConfigValidationResultFailure -> result
                is ConfigValidationResultSuccess -> result.validateAdminConfig()
            }
        }.getOrElse { exception ->
            createScriptErrorResult(script, exception)
        }
    }

    /**
     * Perform additional validation of this [ConfigValidationResultSuccess] using the current admin configuration.
     * After the validation script has been run successfully, some checks can now be performed against the resolved
     * job configurations.
     */
    private fun ConfigValidationResultSuccess.validateAdminConfig(): ConfigValidationResult {
        val adminConfig = adminConfigService.loadAdminConfig(
            context.resolvedConfigurationContext,
            context.ortRun.organizationId
        )

        val validationIssues = mutableListOf<Issue>()

        validateRuleSet(adminConfig, resolvedConfigurations.ruleSet, validationIssues)

        return takeIf { validationIssues.isEmpty() } ?: ConfigValidationResultFailure(issues + validationIssues)
    }

    /**
     * Perform validation of the rule set with the given [ruleSetName] from the given [adminConfig]. Add issues that
     * are found to the given [validationIssues].
     */
    private fun validateRuleSet(adminConfig: AdminConfig, ruleSetName: String?, validationIssues: MutableList<Issue>) {
        fun validateRuleSetFile(file: String) {
            if (!context.configManager.containsFile(context.resolvedConfigurationContext, Path(file))) {
                validationIssues += Issue(
                    Clock.System.now(),
                    ADMIN_CONFIG_VALIDATION_SOURCE,
                    "Unresolvable configuration file '$file'.",
                    Severity.ERROR
                )
            }
        }

        if (ruleSetName != null && ruleSetName !in adminConfig.ruleSetNames) {
            validationIssues += Issue(
                Clock.System.now(),
                ADMIN_CONFIG_VALIDATION_SOURCE,
                "Invalid rule set '$ruleSetName'. " +
                        "Available rule sets are: ${adminConfig.ruleSetNames.joinToString(", ")}.",
                Severity.ERROR
            )
        } else {
            val ruleSet = adminConfig.getRuleSet(ruleSetName)
            validateRuleSetFile(ruleSet.copyrightGarbageFile)
            validateRuleSetFile(ruleSet.licenseClassificationsFile)
            validateRuleSetFile(ruleSet.resolutionsFile)
            validateRuleSetFile(ruleSet.evaluatorRules)
        }
    }
}
