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

package org.ossreviewtoolkit.server.transport.kubernetes.jobmonitor

import com.typesafe.config.ConfigFactory

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.util.ClientBuilder

import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.transport.MessageSenderFactory
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint

import org.slf4j.LoggerFactory

/**
 * The main entrypoint into the job monitoring module. This class reads the configuration and sets up the required
 * helper components.
 */
internal class MonitorComponent(
    /** The configuration of this module. */
    private val configManager: ConfigManager = ConfigManager.create(ConfigFactory.load())
) : KoinComponent {
    companion object {
        /** The prefix for all configuration properties. */
        private const val CONFIG_PREFIX = "jobMonitor"

        /** The configuration property that selects the namespace to watch. */
        private const val NAMESPACE_PROPERTY = "$CONFIG_PREFIX.namespace"

        /** The configuration property defining the interval in which the reaper runs. */
        private const val REAPER_INTERVAL_PROPERTY = "$CONFIG_PREFIX.reaperInterval"

        /** The configuration property to enable or disable the watching component. */
        private const val WATCHING_ENABLED_PROPERTY = "$CONFIG_PREFIX.enableWatching"

        /** The configuration property to enable or disable the Reaper component. */
        private const val REAPER_ENABLED_PROPERTY = "$CONFIG_PREFIX.enableReaper"

        private val logger = LoggerFactory.getLogger(MonitorComponent::class.java)
    }

    init {
        startKoin {
            modules(monitoringModule())
        }
    }

    /** Start this component as it has been configured. */
    suspend fun start() = withContext(Dispatchers.Default) {
        logger.info("Starting Kubernetes Job Monitor Component.")

        if (configManager.getBoolean(REAPER_ENABLED_PROPERTY)) {
            logger.info("Starting Reaper component.")

            val ticker = tickerFlow(configManager.getInt(REAPER_INTERVAL_PROPERTY).seconds)
            val reaper by inject<Reaper>()
            launch { reaper.run(ticker) }
        }

        if (configManager.getBoolean(WATCHING_ENABLED_PROPERTY)) {
            logger.info("Starting watcher component.")

            val monitor by inject<JobMonitor>()
            monitor.watch()
        }
    }

    /**
     * Return a [Module] with the components used by this application.
     */
    private fun monitoringModule(): Module {
        val namespace = configManager.getString(NAMESPACE_PROPERTY)

        return module {
            single { ClientBuilder.defaultClient() }
            single { BatchV1Api(get()) }
            single { CoreV1Api(get()) }

            single { MessageSenderFactory.createSender(OrchestratorEndpoint, configManager) }

            single { JobWatchHelper.create(get(), namespace) }
            single { JobHandler(get(), get(), get(), namespace) }
            single { FailedJobNotifier(get()) }
            singleOf(::JobMonitor)
            single { Reaper(get(), configManager.getInt(REAPER_INTERVAL_PROPERTY).seconds) }
        }
    }
}
