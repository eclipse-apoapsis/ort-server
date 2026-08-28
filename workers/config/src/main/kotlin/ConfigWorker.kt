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

import org.eclipse.apoapsis.ortserver.components.adminconfig.AdminConfigService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.dao.dbQuery
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

    /** The service for accessing plugin information. */
    private val pluginService: PluginService,

    /** The service for accessing plugin templates. */
    private val pluginTemplateService: PluginTemplateService
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
            val jobConfigContext = context.ortRun.jobConfigContext?.let(::Context)
            val resolvedJobConfigContext = context.configManager.resolveContext(jobConfigContext)

            logger.info(
                "Provided configuration context '{}' was resolved to '{}'.",
                context.ortRun.jobConfigContext,
                resolvedJobConfigContext.name
            )

            // Resolve the default package managers before the validation script runs so the script can see and
            // potentially override them.
            val baseConfigs = context.resolveDefaultPackageManagers(context.ortRun.jobConfigs)

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
                    createValidationWorkerContext(context, resolvedJobConfigContext, baseConfigs)
                )

                validationScriptRunner.runScript(validationScript).also {
                    logger.debug("Issues returned by validation script: {}.", it.issues)
                }
            } else {
                logger.info("Skipping validation as no script exists.")

                ConfigValidationResultSuccess(baseConfigs)
            }

            val validationResult = when (validationScriptResult) {
                is ConfigValidationResultSuccess ->
                    validateAdminConfig(resolvedJobConfigContext, validationScriptResult)

                is ConfigValidationResultFailure -> validationScriptResult
            }

            storeResult(ortRunId, resolvedJobConfigContext, validationResult)

            when (validationResult) {
                is ConfigValidationResultSuccess -> RunResult.Success

                is ConfigValidationResultFailure ->
                    RunResult.Failed(IllegalArgumentException("Parameter validation failed"))
            }
        }
    }.getOrElse { RunResult.Failed(it) }

    private fun validateAdminConfig(
        resolvedJobConfigContext: Context,
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
        logger.error("Error during admin configuration validation.", e)

        val issue = createIssue(
            message = "Could not load admin config: '${e.message}'. This is a problem with the configuration of ORT " +
                    "Server, please contact the administrator."
        )

        ConfigValidationResultFailure(validationScriptResult.issues + issue)
    }

    private suspend fun storeResult(
        ortRunId: Long,
        resolvedJobConfigContext: Context,
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

        db.dbQuery {
            ortRunRepository.update(
                id = ortRunId,
                resolvedJobConfigs = resolvedJobConfigs,
                resolvedJobConfigContext = resolvedJobConfigContext.name.asPresent(),
                issues = validationResult.optionalIssues,
                labels = labels
            )
        }
    }

    /**
     * Create a [WorkerContext] that delegates to the given [context], but returns the provided
     * [resolvedJobConfigContext] and [baseConfigs]. This is needed because the [WorkerContext.ortRun] object contained
     * in the original [WorkerContext] does not have the resolved configuration context yet; it is updated at the end
     * of the worker execution. However, the config validation script needs the right configuration context. The
     * [baseConfigs] override ensures the script sees the pre-resolved job configurations, including the default
     * package managers.
     */
    private fun createValidationWorkerContext(
        context: WorkerContext,
        resolvedJobConfigContext: Context,
        baseConfigs: JobConfigurations
    ): WorkerContext {
        val runWithConfigContext = context.ortRun.copy(
            resolvedJobConfigContext = resolvedJobConfigContext.name,
            jobConfigs = baseConfigs
        )

        return object : WorkerContext by context {
            override val ortRun: OrtRun
                get() = runWithConfigContext
        }
    }

    /**
     * Return a copy of the given [jobConfigs] with the default package managers filled in for the analyzer
     * configuration if [AnalyzerJobConfiguration.enabledPackageManagers][org.eclipse.apoapsis.ortserver.model
     * .AnalyzerJobConfiguration.enabledPackageManagers] is null or empty.
     */
    private fun WorkerContext.resolveDefaultPackageManagers(jobConfigs: JobConfigurations): JobConfigurations {
        if (!jobConfigs.analyzer.enabledPackageManagers.isNullOrEmpty()) return jobConfigs

        val defaults = getDefaultPackageManagers(
            pluginService,
            pluginTemplateService,
            OrganizationId(ortRun.organizationId)
        )

        logger.info("Determined default package managers: $defaults")

        return jobConfigs.copy(
            analyzer = jobConfigs.analyzer.copy(enabledPackageManagers = defaults)
        )
    }
}
