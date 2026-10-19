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

import kotlin.coroutines.cancellation.CancellationException

import org.eclipse.apoapsis.ortserver.components.adminconfig.AdminConfigService
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.config.ResolvedConfigContext
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.model.util.asPresent
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory

import org.jetbrains.exposed.v1.jdbc.Database

import org.slf4j.LoggerFactory

/**
 * A worker implementation that checks and transforms the configuration of an ORT run using a [ValidationScriptRunner].
 */
class ConfigWorker(
    /** The database. */
    private val db: Database,

    /** The repository for accessing ORT run instances. */
    private val ortRunRepository: OrtRunRepository,

    /** The factory for obtaining a worker context. */
    private val contextFactory: WorkerContextFactory,

    /** The service to access the admin configuration. */
    private val adminConfigService: AdminConfigService,

    /** The applicator for the plugin templates configured by administrators. */
    private val pluginTemplateApplicator: PluginTemplateApplicator
) {
    companion object {
        /** Constant for the path to the script that validates and transforms parameters. */
        val VALIDATION_SCRIPT_PATH = Path("ort-server.params.kts")

        private val logger = LoggerFactory.getLogger(ConfigWorker::class.java)
    }

    /**
     * Execute the config validation on the ORT run with the given [ortRunId].
     */
    suspend fun run(ortRunId: Long): RunResult = runCatching {
        logger.info("Running config worker for run '$ortRunId'.")

        contextFactory.withContext(ortRunId) { context ->
            val resolvedJobConfigContext = ResolvedConfigContext(
                checkNotNull(context.ortRun.resolvedJobConfigContext) {
                    "The config context of ORT run '$ortRunId' was not resolved when the run was created."
                }
            )

            logger.info("Using the configuration context '{}'.", resolvedJobConfigContext.name)

            // Apply the plugin templates before the validation script runs so that the script can override any value
            // an administrator has configured.
            val validationResult = runCatching {
                pluginTemplateApplicator.applyTemplates(
                    context.ortRun.jobConfigs,
                    OrganizationId(context.ortRun.organizationId)
                )
            }.fold(
                onSuccess = { baseConfigs -> validateConfigs(context, resolvedJobConfigContext, baseConfigs) },
                onFailure = ::createPluginTemplateFailure
            )

            storeResult(ortRunId, validationResult)

            when (validationResult) {
                is ConfigValidationResultSuccess -> RunResult.Success

                is ConfigValidationResultFailure ->
                    RunResult.Failed(IllegalArgumentException("Parameter validation failed"))
            }
        }
    }.getOrElse { RunResult.Failed(it) }

    /**
     * Validate and transform the given [baseConfigs] by running the validation script found in the given
     * [resolvedJobConfigContext] - if any - and afterwards checking the result against the admin configuration.
     */
    private suspend fun validateConfigs(
        context: WorkerContext,
        resolvedJobConfigContext: ResolvedConfigContext,
        baseConfigs: JobConfigurations
    ): ConfigValidationResult {
        // TODO: Currently the path to the validation script is hard-coded. It may make sense to have it
        //       configurable.
        val validationScriptExists = context.configManager.containsFile(
            resolvedJobConfigContext,
            VALIDATION_SCRIPT_PATH
        )

        val validationScriptResult = if (validationScriptExists) {
            logger.info("Running validation script.")

            val validationScript = context.configManager.getFileAsString(
                resolvedJobConfigContext,
                VALIDATION_SCRIPT_PATH
            )
            val validationScriptRunner = ValidationScriptRunner(
                createValidationWorkerContext(context, baseConfigs)
            )

            validationScriptRunner.runScript(validationScript).also {
                logger.debug("Issues returned by validation script: {}.", it.issues)
            }
        } else {
            logger.info("Skipping validation as no script exists.")

            ConfigValidationResultSuccess(baseConfigs)
        }

        return when (validationScriptResult) {
            is ConfigValidationResultSuccess -> validateAdminConfig(resolvedJobConfigContext, validationScriptResult)
            is ConfigValidationResultFailure -> validationScriptResult
        }
    }

    /**
     * Create a [ConfigValidationResultFailure] with an issue for the given [exception] that was thrown while applying
     * the plugin templates.
     */
    private fun createPluginTemplateFailure(exception: Throwable): ConfigValidationResultFailure {
        logger.error("Error while applying the plugin templates.", exception)

        val issue = createIssue(
            message = "Could not apply the plugin templates: '${exception.message}'. This is a problem with the " +
                    "configuration of ORT Server, please contact the administrator.",
            source = PLUGIN_TEMPLATE_SOURCE
        )

        return ConfigValidationResultFailure(listOf(issue))
    }

    private suspend fun validateAdminConfig(
        resolvedJobConfigContext: ResolvedConfigContext,
        validationScriptResult: ConfigValidationResultSuccess
    ) = runCatching {
        logger.info("Validating admin config.")

        val adminConfig = adminConfigService.loadAdminConfig(resolvedJobConfigContext, true)

        val validationIssues =
            AdminConfigValidator.validate(adminConfig, validationScriptResult.resolvedConfigurations)

        if (validationIssues.isNotEmpty()) {
            ConfigValidationResultFailure(validationScriptResult.issues + validationIssues)
        } else {
            validationScriptResult
        }
    }.getOrElse { e ->
        if (e is CancellationException) throw e

        logger.error("Error during admin configuration validation.", e)

        val issue = createIssue(
            message = "Could not load admin config: '${e.message}'. This is a problem with the configuration of ORT " +
                    "Server, please contact the administrator.",
            source = ADMIN_CONFIG_VALIDATION_SOURCE
        )

        ConfigValidationResultFailure(validationScriptResult.issues + issue)
    }

    private suspend fun storeResult(
        ortRunId: Long,
        validationResult: ConfigValidationResult
    ) {
        val labels: OptionalValue<Map<String, String>>
        val resolvedJobConfigs: OptionalValue<JobConfigurations>

        when (validationResult) {
            is ConfigValidationResultSuccess -> {
                resolvedJobConfigs = validationResult.resolvedConfigurations.asPresent()
                labels = validationResult.optionalLabels
            }

            is ConfigValidationResultFailure -> {
                resolvedJobConfigs = OptionalValue.Absent
                labels = OptionalValue.Absent
            }
        }

        db.transaction {
            ortRunRepository.update(
                id = ortRunId,
                resolvedJobConfigs = resolvedJobConfigs,
                issues = validationResult.optionalIssues,
                labels = labels
            )
        }
    }

    /**
     * Create a [WorkerContext] that delegates to the given [context], but returns the provided [baseConfigs] as the
     * job configurations of the [WorkerContext.ortRun]. This ensures the validation script sees the pre-resolved job
     * configurations, including the values contributed by the plugin templates.
     */
    private fun createValidationWorkerContext(context: WorkerContext, baseConfigs: JobConfigurations): WorkerContext {
        val runWithBaseConfigs = context.ortRun.copy(jobConfigs = baseConfigs)

        return object : WorkerContext by context {
            override val ortRun: OrtRun
                get() = runWithBaseConfigs
        }
    }
}
