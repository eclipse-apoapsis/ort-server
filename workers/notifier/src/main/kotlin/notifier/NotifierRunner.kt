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

import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.readConfigFileValueWithDefault
import org.eclipse.apoapsis.ortserver.workers.common.resolvedConfigurationContext

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.NotifierConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.notifier.Notifier
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME

class NotifierRunner(
    /** The service for accessing the admin configuration. */
    private val configAdminService: AdminConfigService
) {
    /**
     * Invoke the [Notifier] for the current ORT run using the given [ortResult], [jobConfigurations], and
     * [workerContext].
     * The notification script to be executed is specified in the admin configuration.
     */
    fun run(
        ortResult: OrtResult,
        jobConfigurations: JobConfigurations?,
        workerContext: WorkerContext
    ) {
        val adminConfig = configAdminService.loadAdminConfig(
            workerContext.resolvedConfigurationContext,
            workerContext.ortRun.organizationId
        )
        val notifierConfig = adminConfig.notifierConfig

        val sendMailConfiguration = notifierConfig.mail?.takeUnless { notifierConfig.disableMailNotifications }?.let {
            it.copy(
                username = workerContext.configManager.getSecret(Path(it.username)),
                password = workerContext.configManager.getSecret(Path(it.password)),
            )
        }?.mapToOrt()

        val jiraConfiguration = notifierConfig.jira?.takeUnless { notifierConfig.disableJiraNotifications }?.let {
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

        val resolutionFilePath = adminConfig.getRuleSet(jobConfigurations?.ruleSet).resolutionsFile
        val resolutionsFromFile = workerContext.configManager.readConfigFileValueWithDefault(
            path = resolutionFilePath,
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

        val script = workerContext.configManager.getFileAsString(
            workerContext.resolvedConfigurationContext,
            Path(notifierConfig.notifierRules)
        )
        notifier.run(script)
    }
}
