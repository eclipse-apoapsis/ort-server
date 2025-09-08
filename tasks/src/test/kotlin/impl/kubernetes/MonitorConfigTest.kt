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

package org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

import org.eclipse.apoapsis.ortserver.config.ConfigManager

class MonitorConfigTest : StringSpec({
    "The configuration should be loaded correctly" {
        val timeoutMap = mapOf(
            "config" to "1",
            "analyzer" to "90",
            "advisor" to "2",
            "scanner" to "1440",
            "evaluator" to "5",
            "reporter" to "10",
            "notifier" to "4"
        )
        val monitorConfigMap = mapOf(
            "namespace" to "test-namespace",
            "reaperMaxAge" to "555",
            "lostJobsMinAge" to "600",
            "recentlyProcessedInterval" to "66",
            "timeouts" to timeoutMap
        )
        val configMap = mapOf("jobMonitor" to monitorConfigMap)
        val configManager = ConfigManager.create(ConfigFactory.parseMap(configMap))

        val monitorConfig = MonitorConfig.create(configManager)

        monitorConfig.namespace shouldBe "test-namespace"
        monitorConfig.reaperMaxAge shouldBe 555.seconds
        monitorConfig.lostJobsMinAge shouldBe 600.seconds
        monitorConfig.recentlyProcessedInterval shouldBe 66.seconds
        monitorConfig.timeoutConfig shouldBe TimeoutConfig(
            configTimeout = 1.minutes,
            analyzerTimeout = 90.minutes,
            advisorTimeout = 2.minutes,
            scannerTimeout = 1440.minutes,
            evaluatorTimeout = 5.minutes,
            reporterTimeout = 10.minutes,
            notifierTimeout = 4.minutes
        )
    }

    "The configuration should be loaded from environment variables" {
        val environment = mapOf(
            "MONITOR_NAMESPACE" to "test-namespace",
            "MONITOR_REAPER_MAX_AGE" to "555",
            "MONITOR_LOST_JOBS_MIN_AGE" to "600",
            "MONITOR_RECENTLY_PROCESSED_INTERVAL" to "66",
            "MONITOR_TIMEOUT_CONFIG" to "1",
            "MONITOR_TIMEOUT_ANALYZER" to "90",
            "MONITOR_TIMEOUT_ADVISOR" to "2",
            "MONITOR_TIMEOUT_SCANNER" to "1440",
            "MONITOR_TIMEOUT_EVALUATOR" to "5",
            "MONITOR_TIMEOUT_REPORTER" to "10",
            "MONITOR_TIMEOUT_NOTIFIER" to "4"
        )

        withEnvironment(environment) {
            ConfigFactory.invalidateCaches()
            val configManager = ConfigManager.create(ConfigFactory.load())

            val monitorConfig = MonitorConfig.create(configManager)

            monitorConfig.namespace shouldBe "test-namespace"
            monitorConfig.reaperMaxAge shouldBe 555.seconds
            monitorConfig.lostJobsMinAge shouldBe 600.seconds
            monitorConfig.recentlyProcessedInterval shouldBe 66.seconds
            monitorConfig.timeoutConfig shouldBe TimeoutConfig(
                configTimeout = 1.minutes,
                analyzerTimeout = 90.minutes,
                advisorTimeout = 2.minutes,
                scannerTimeout = 1440.minutes,
                evaluatorTimeout = 5.minutes,
                reporterTimeout = 10.minutes,
                notifierTimeout = 4.minutes
            )
        }
    }
})
