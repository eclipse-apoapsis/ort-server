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

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.model.util.asPresent
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory

import org.jetbrains.exposed.sql.Database

import org.slf4j.LoggerFactory

/**
 * A worker implementation that checks and transforms the configuration of an ORT run using a [ConfigValidator].
 */
class ConfigWorker(
    /** The database. */
    private val db: Database,

    /** The repository for accessing ORT run instances. */
    private val ortRunRepository: OrtRunRepository,

    /** The factory for obtaining a worker context. */
    private val contextFactory: WorkerContextFactory,

    /** The object for accessing configuration data. */
    private val configManager: ConfigManager
) {
    companion object {
        /** Constant for the path to the script that validates and transforms parameters. */
        val VALIDATION_SCRIPT_PATH = Path("ort-server.params.kts")

        private val logger = LoggerFactory.getLogger(ConfigWorker::class.java)

        /**
         * Extract an [OptionalValue] with the labels to update for this result. If there are no new labels to add,
         * return an [OptionalValue.Absent] result.
         */
        private fun ConfigValidationResultSuccess.labelsToUpdate(): OptionalValue<Map<String, String>> =
            labels.takeUnless { it.isEmpty() }?.asPresent() ?: OptionalValue.Absent
    }

    /**
     * Execute the config validation on the ORT run with the given [ortRunId].
     */
    suspend fun run(ortRunId: Long): RunResult = runCatching {
        val context = contextFactory.createContext(ortRunId)

        val jobConfigContext = context.ortRun.jobConfigContext?.let(::Context)
        val resolvedJobConfigContext = configManager.resolveContext(jobConfigContext)

        logger.info(
            "Provided configuration context '{}' was resolved to '{}'.",
            context.ortRun.jobConfigContext,
            resolvedJobConfigContext.name
        )

        // TODO: Currently the path to the validation script is hard-coded. It may make sense to have it configurable.
        val validationScript = configManager.getFileAsString(resolvedJobConfigContext, VALIDATION_SCRIPT_PATH)

        val validator = ConfigValidator.create(createValidationWorkerContext(context, resolvedJobConfigContext))
        val validationResult = validator.validate(validationScript)

        logger.debug("Issues returned by validation script: {}.", validationResult.issues)

        val (result, configs, labels) = when (validationResult) {
            is ConfigValidationResultSuccess -> Triple(
                RunResult.Success,
                validationResult.resolvedConfigurations.asPresent(),
                validationResult.labelsToUpdate()
            )

            is ConfigValidationResultFailure -> Triple(
                RunResult.Failed(IllegalArgumentException("Parameter validation failed.")),
                OptionalValue.Absent,
                OptionalValue.Absent
            )
        }

        db.dbQuery {
            ortRunRepository.update(
                ortRunId,
                resolvedJobConfigs = configs,
                issues = validationResult.issues.asPresent(),
                labels = labels,
                resolvedJobConfigContext = resolvedJobConfigContext.name.asPresent()
            )
        }

        result
    }.getOrElse { RunResult.Failed(it) }

    /**
     * Create a [WorkerContext] that delegates to the given [context], but returns the provided
     * [resolvedJobConfigContext]. This is needed because the [WorkerContext.ortRun] object contained in the original
     * [WorkerContext] does not have the resolved configuration context yet; it is updated at the end of the worker
     * execution. However, the config validation script needs the right configuration context.
     */
    private fun createValidationWorkerContext(
        context: WorkerContext,
        resolvedJobConfigContext: Context
    ): WorkerContext {
        val runWithConfigContext = context.ortRun.copy(resolvedJobConfigContext = resolvedJobConfigContext.name)

        return object : WorkerContext by context {
            override val ortRun: OrtRun
                get() = runWithConfigContext
        }
    }
}
