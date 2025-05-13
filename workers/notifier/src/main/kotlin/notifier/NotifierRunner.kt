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

package org.eclipse.apoapsis.ortserver.workers.notifier

import java.time.Instant

import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.NotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.readConfigFileValueWithDefault
import org.eclipse.apoapsis.ortserver.workers.common.resolvedConfigurationContext

import org.ossreviewtoolkit.model.NotifierRun
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.NotifierConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.notifier.Notifier
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(NotifierRunner::class.java)

class NotifierRunner {
    /**
     * The notifier result used when the notifier failed, as a failed notifier should effectively be ignored.
     */
    private val dummyNotifierResult = NotifierRun(Instant.now(), Instant.now())

    /**
     * Invoke the [Notifier] for the current ORT run.
     * The notification script is downloaded from the configuration using the path specified in [config].
     */
    fun run(
        ortResult: OrtResult,
        config: NotifierJobConfiguration,
        workerContext: WorkerContext
    ): NotifierRunnerResult {
        return runCatching {
            val script = config.notifierRules?.let {
                workerContext.configManager.getFileAsString(
                    workerContext.resolvedConfigurationContext,
                    Path(it)
                )
            } ?: throw IllegalArgumentException("The notification script path is not specified in the config.", null)

            val sendMailConfiguration = config.mail?.mailServerConfiguration?.let {
                it.copy(
                    username = workerContext.configManager.getSecret(Path(it.username)),
                    password = workerContext.configManager.getSecret(Path(it.password)),
                )
            }?.mapToOrt()

            val jiraConfiguration = config.jira?.jiraRestClientConfiguration?.let {
                it.copy(
                    username = workerContext.configManager.getSecret(Path(it.username)),
                    password = workerContext.configManager.getSecret(Path(it.password)),
                )
            }?.mapToOrt()

            val notifierConfiguration = NotifierConfiguration(
                mail = sendMailConfiguration,
                jira = jiraConfiguration
            )

            val resolutionsFromOrtResult = ortResult.repository.config.resolutions

            val resolutionsFromFile = workerContext.configManager.readConfigFileValueWithDefault(
                path = config.resolutionsFile,
                defaultPath = ORT_RESOLUTIONS_FILENAME,
                fallbackValue = Resolutions(),
                workerContext.resolvedConfigurationContext
            )

            val resolutionProvider = DefaultResolutionProvider(resolutionsFromOrtResult.merge(resolutionsFromFile))

            val notifier = Notifier(
                ortResult = ortResult,
                config = notifierConfiguration,
                resolutionProvider = resolutionProvider
            )

            val notifierRun = notifier.run(script)

            NotifierRunnerResult(notifierRun)
        }.getOrElse { e ->
            logger.warn("Failed to send notification(s).", e)

            NotifierRunnerResult(dummyNotifierResult)
        }
    }
}

data class NotifierRunnerResult(
    val notifierRun: NotifierRun
)
