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
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.scriptsInstancesSharing
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

import org.ossreviewtoolkit.utils.script.ScriptRunner

import org.slf4j.LoggerFactory

/**
 * A class for validating and transforming the parameters of an ORT run using a validation script.
 *
 * An instance can be created for a specific [WorkerContext]. It can then be used to run a specific validation and
 * transformation script and obtain the results produced by this script.
 */
class ConfigValidator private constructor(
    /** The current [WorkerContext]. */
    context: WorkerContext
) : ScriptRunner<ConfigValidationResult>() {
    companion object {
        private val logger = LoggerFactory.getLogger(ConfigValidator::class.java)

        /**
         * Return a new instance of [ConfigValidator] to validate the parameters of the ORT run stored in the given
         * [context].
         */
        fun create(context: WorkerContext): ConfigValidator = ConfigValidator(context)

        /**
         * Create a [ConfigValidationResult] for the case that there was an error during the execution of the given
         * [validation script][script] caused by the given [exception].
         */
        private fun createScriptErrorResult(script: String, exception: Throwable): ConfigValidationResult =
            ConfigValidationResultFailure(
                issues = listOf(
                    createIssue(
                        "Error when executing validation script. This is a problem with the configuration " +
                                "of ORT Server.",
                        INVALID_SCRIPT_SOURCE
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

    override fun runScript(script: SourceCode): ConfigValidationResult {
        val executedScript = run(script).scriptInstance as ValidationScriptTemplate

        return when (val result = executedScript.validationResult) {
            is ConfigValidationResultFailure -> result
            is ConfigValidationResultSuccess -> result
        }
    }

    /**
     * Validate the parameters of the ORT run from the current context by executing the given [script]. Return the
     * result of this script. If the script execution fails, return a failure result as well with the information
     * available about the failure.
     */
    fun validate(script: String): ConfigValidationResult = runCatching {
        runScript(script)
    }.getOrElse { exception ->
        createScriptErrorResult(script, exception)
    }
}
