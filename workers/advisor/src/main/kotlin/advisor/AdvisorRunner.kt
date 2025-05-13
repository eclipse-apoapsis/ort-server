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

package org.eclipse.apoapsis.ortserver.workers.advisor

import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.advisor.Advisor
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.AdvisorConfiguration

import org.slf4j.LoggerFactory

internal class AdvisorRunner {
    companion object {
        private val logger = LoggerFactory.getLogger(AdvisorRunner::class.java)
    }

    suspend fun run(context: WorkerContext, ortResult: OrtResult, config: AdvisorJobConfiguration): OrtResult {
        logger.info("Advisor run with these advisors: '{}'.", config.advisors)

        val providerFactories = config.advisors.mapNotNull { AdviceProviderFactory.ALL[it] }
        if (providerFactories.size < config.advisors.size) {
            val invalidAdvisors = config.advisors.filter { it !in AdviceProviderFactory.ALL }
            logger.error("The following advisors could not be resolved: {}.", invalidAdvisors)
        }

        val pluginConfigs = context.resolvePluginConfigSecrets(config.config)
        val advisorConfig = AdvisorConfiguration(
            config.skipExcluded,
            pluginConfigs.mapValues { it.value.mapToOrt() }
        )

        val advisor = Advisor(providerFactories, advisorConfig)

        return advisor.advise(ortResult, config.skipExcluded)
    }
}
