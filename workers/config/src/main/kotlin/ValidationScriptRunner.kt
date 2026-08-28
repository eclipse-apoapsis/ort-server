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
class ValidationScriptRunner(
    /** The current [WorkerContext]. */
    context: WorkerContext
) : ScriptRunner<ConfigValidationResult>() {
    companion object {
        private val logger = LoggerFactory.getLogger(ValidationScriptRunner::class.java)
    }

    override val compConfig = createJvmCompilationConfigurationFromTemplate<ValidationScriptTemplate>()

    override val evalConfig = ScriptEvaluationConfiguration {
        constructorArgs(context, Clock.System.now())
        scriptsInstancesSharing(true)
    }

    /**
     * Run the provided [script] and return the result. If script execution fails, return a
     * [ConfigValidationResultFailure].
     */
    override fun runScript(script: SourceCode): ConfigValidationResult = runCatching {
        (run(script).scriptInstance as ValidationScriptTemplate).validationResult
    }.getOrElse { e ->
        val issue = createIssue(
            message = "Error when executing validation script. This is a problem with the configuration of ORT Server.",
            source = INVALID_SCRIPT_SOURCE
        )

        ConfigValidationResultFailure(issues = listOf(issue)).also {
            logger.error("Error when executing validation script.", e)
            logger.debug("Content of the script:\n{}", script)
        }
    }
}
