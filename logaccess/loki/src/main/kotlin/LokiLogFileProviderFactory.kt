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

package org.ossreviewtoolkit.server.logaccess.loki

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.logaccess.LogFileProvider
import org.ossreviewtoolkit.server.logaccess.LogFileProviderFactory

import org.slf4j.LoggerFactory

/**
 * Implementation of the [LogFileProviderFactory] interface for creating a [LogFileProvider] to retrieve log data
 * from Grafana Loki.
 */
class LokiLogFileProviderFactory : LogFileProviderFactory {
    companion object {
        /** The name of this provider implementation. */
        const val NAME = "loki"

        private val logger = LoggerFactory.getLogger(LokiLogFileProviderFactory::class.java)
    }

    override val name: String
        get() = NAME

    override fun createProvider(config: ConfigManager): LogFileProvider {
        logger.info("Creating a LogFileProvider instance for Grafana Loki.")

        val lokiConfig = LokiConfig.create(config)
        if (logger.isDebugEnabled) {
            val maskedConfig = lokiConfig.password?.let { lokiConfig.copy(password = "***") } ?: lokiConfig
            logger.debug("Configuration for Loki provider: {}.", maskedConfig)
        }

        return LokiLogFileProvider(lokiConfig)
    }
}
