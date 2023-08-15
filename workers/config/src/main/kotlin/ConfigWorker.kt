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

package org.ossreviewtoolkit.server.workers.config

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Context
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.dao.dbQuery
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.model.util.asPresent
import org.ossreviewtoolkit.server.workers.common.RunResult
import org.ossreviewtoolkit.server.workers.common.context.WorkerContextFactory

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
        val VALIDATION_SCRIPT_PATH = Path("parameters.kts")
    }

    /**
     * Execute the config validation on the ORT run with the given [ortRunId].
     */
    suspend fun run(ortRunId: Long): RunResult = runCatching {
        val context = contextFactory.createContext(ortRunId)

        val configContext = context.ortRun.configContext?.let(::Context)
        val resolvedContext = configManager.resolveContext(configContext)

        // TODO: Currently the path to the validation script is hard-coded. It may make sense to have it configurable.
        val validationScript = configManager.getFileAsString(resolvedContext, VALIDATION_SCRIPT_PATH)

        val validator = ConfigValidator.create(context)
        val validationResult = validator.validate(validationScript)

        val (result, resolvedConfig) = when (validationResult) {
            is ConfigValidationResultSuccess ->
                RunResult.Success to validationResult.resolvedConfigurations.asPresent()

            is ConfigValidationResultFailure ->
                RunResult.Failed(IllegalArgumentException("Parameter validation failed.")) to OptionalValue.Absent
        }

        db.dbQuery {
            ortRunRepository.update(
                ortRunId,
                resolvedConfig = resolvedConfig,
                issues = validationResult.issues.asPresent(),
                resolvedConfigContext = resolvedContext.name.asPresent()
            )
        }

        result
    }.getOrElse { RunResult.Failed(it) }
}
