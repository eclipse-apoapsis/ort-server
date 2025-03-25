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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path

/**
 * A class that holds configuration settings related to timeouts for the single workers. The idea is that workers
 * that are running longer than a configured time are considered as stuck and are terminated. Since the execution
 * times for workers vary greatly depending on their type, timeouts can be configured for each worker type
 * separately.
 */
data class TimeoutConfig(
    /** The timeout for the Config worker. */
    val configTimeout: Duration,

    /** The timeout for the Analyzer worker. */
    val analyzerTimeout: Duration,

    /** The timeout for the Advisor worker. */
    val advisorTimeout: Duration,

    /** The timeout for the Scanner worker. */
    val scannerTimeout: Duration,

    /** The timeout for the Evaluator worker. */
    val evaluatorTimeout: Duration,

    /** The timeout for the Reporter worker. */
    val reporterTimeout: Duration,

    /** The timeout for the Notifier worker. */
    val notifierTimeout: Duration
)

/**
 * A class collecting the configuration options supported by the Kubernetes Job Monitor component.
 */
data class MonitorConfig(
    /** The name of the Kubernetes namespace this component should monitor. */
    val namespace: String,

    /**
     * The maximum age of a job for being deleted by the Reaper. During a Reaper run, it deletes all completed that
     * are older than this value.
     */
    val reaperMaxAge: Duration,

    /**
     * The minimum age of a job in the database to be taken into account by the lost job finder component. This is
     * needed to prevent certain race conditions with jobs that have just been added to the database, but are not yet
     * propagated to Kubernetes.
     */
    val lostJobsMinAge: Duration,

    /**
     * A duration that defines an interval in which a job should be considered as recently processed. The job handler
     * keeps a list of the jobs that have been deleted during this interval to prevent duplicate processing of jobs.
     */
    val recentlyProcessedInterval: Duration,

    /** The configuration of the timeouts for the single workers. */
    val timeoutConfig: TimeoutConfig,
) {
    companion object {
        /** The prefix for all configuration properties. */
        private const val CONFIG_PREFIX = "jobMonitor"

        /** The configuration property that selects the namespace to watch. */
        private const val NAMESPACE_PROPERTY = "namespace"

        /**
         * The configuration property defining the maximum age of a completed job (in seconds) to become subject of
         * the Reaper. The Reaper deletes jobs that are older than this value.
         */
        private const val REAPER_MAX_AGE_PROPERTY = "reaperMaxAge"

        /** The configuration property defining the minimum age of a job (in seconds) to be considered lost. */
        private const val LOST_JOBS_MIN_AGE_PROPERTY = "lostJobsMinAge"

        /**
         * The configuration property defining the interval in which a job should be considered as recently processed
         * (in seconds).
         */
        private const val RECENTLY_PROCESSED_INTERVAL_PROPERTY = "recentlyProcessedInterval"

        /** The section that defines the timeouts for the single workers. */
        private const val TIMEOUTS_SECTION = "timeouts"

        /** The configuration property defining the timeout for the Config worker. */
        private const val TIMEOUT_CONFIG_PROPERTY = "config"

        /** The configuration property defining the timeout for the Analyzer worker. */
        private const val TIMEOUT_ANALYZER_PROPERTY = "analyzer"

        /** The configuration property defining the timeout for the Advisor worker. */
        private const val TIMEOUT_ADVISOR_PROPERTY = "advisor"

        /** The configuration property defining the timeout for the Scanner worker. */
        private const val TIMEOUT_SCANNER_PROPERTY = "scanner"

        /** The configuration property defining the timeout for the Evaluator worker. */
        private const val TIMEOUT_EVALUATOR_PROPERTY = "evaluator"

        /** The configuration property defining the timeout for the Reporter worker. */
        private const val TIMEOUT_REPORTER_PROPERTY = "reporter"

        /** The configuration property defining the timeout for the Notifier worker. */
        private const val TIMEOUT_NOTIFIER_PROPERTY = "notifier"

        /**
         * Create a [MonitorConfig] from the settings stored in the given [configManager].
         */
        fun create(configManager: ConfigManager): MonitorConfig {
            val config = configManager.subConfig(Path(CONFIG_PREFIX))

            return MonitorConfig(
                namespace = config.getString(NAMESPACE_PROPERTY),
                reaperMaxAge = config.getInt(REAPER_MAX_AGE_PROPERTY).seconds,
                lostJobsMinAge = config.getInt(LOST_JOBS_MIN_AGE_PROPERTY).seconds,
                recentlyProcessedInterval = config.getInt(RECENTLY_PROCESSED_INTERVAL_PROPERTY).seconds,
                timeoutConfig = createTimeoutConfig(config)
            )
        }

        /**
         * Create a [TimeoutConfig] from the corresponding subsection of the given [config].
         */
        private fun createTimeoutConfig(config: ConfigManager): TimeoutConfig {
            val timeouts = config.subConfig(Path(TIMEOUTS_SECTION))

            return TimeoutConfig(
                configTimeout = timeouts.getInt(TIMEOUT_CONFIG_PROPERTY).minutes,
                analyzerTimeout = timeouts.getInt(TIMEOUT_ANALYZER_PROPERTY).minutes,
                advisorTimeout = timeouts.getInt(TIMEOUT_ADVISOR_PROPERTY).minutes,
                scannerTimeout = timeouts.getInt(TIMEOUT_SCANNER_PROPERTY).minutes,
                evaluatorTimeout = timeouts.getInt(TIMEOUT_EVALUATOR_PROPERTY).minutes,
                reporterTimeout = timeouts.getInt(TIMEOUT_REPORTER_PROPERTY).minutes,
                notifierTimeout = timeouts.getInt(TIMEOUT_NOTIFIER_PROPERTY).minutes
            )
        }
    }
}
