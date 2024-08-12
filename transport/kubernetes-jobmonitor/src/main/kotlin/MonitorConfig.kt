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

package org.eclipse.apoapsis.ortserver.transport.kubernetes.jobmonitor

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path

/**
 * A class collecting the configuration options supported by the Kubernetes Job Monitor component.
 */
data class MonitorConfig(
    /** The name of the Kubernetes namespace this component should monitor. */
    val namespace: String,

    /** The interval in which the Reaper component should run. */
    val reaperInterval: Duration,

    /**
     * The maximum age of a job for being deleted by the Reaper. During a Reaper run, it deletes all completed that
     * are older than this value.
     */
    val reaperMaxAge: Duration,

    /** The interval in which the lost jobs detection should run. */
    val lostJobsInterval: Duration,

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

    /** Flag whether the watcher component should be active. */
    val watchingEnabled: Boolean,

    /** Flag whether the Reaper component should be active. */
    val reaperEnabled: Boolean,

    /** Flag whether the lost jobs detection component should be active. */
    val lostJobsEnabled: Boolean
) {
    companion object {
        /** The prefix for all configuration properties. */
        private const val CONFIG_PREFIX = "jobMonitor"

        /** The configuration property that selects the namespace to watch. */
        private const val NAMESPACE_PROPERTY = "namespace"

        /** The configuration property defining the interval in which the reaper should run (in seconds). */
        private const val REAPER_INTERVAL_PROPERTY = "reaperInterval"

        /**
         * The configuration property defining the maximum age of a completed job (in seconds) to become subject of
         * the Reaper. The Reaper deletes jobs that are older than this value.
         */
        private const val REAPER_MAX_AGE_PROPERTY = "reaperMaxAge"

        /**
         * The configuration property defining the interval in which the lost jobs detection should run (in seconds).
         */
        private const val LOST_JOBS_INTERVAL_PROPERTY = "lostJobsInterval"

        /** The configuration property defining the minimum age of a job (in seconds) to be considered lost. */
        private const val LOST_JOBS_MIN_AGE_PROPERTY = "lostJobsMinAge"

        /**
         * The configuration property defining the interval in which a job should be considered as recently processed
         * (in seconds).
         */
        private const val RECENTLY_PROCESSED_INTERVAL_PROPERTY = "recentlyProcessedInterval"

        /** The configuration property to enable or disable the watcher component. */
        private const val WATCHING_ENABLED_PROPERTY = "enableWatching"

        /** The configuration property to enable or disable the Reaper component. */
        private const val REAPER_ENABLED_PROPERTY = "enableReaper"

        /** The configuration property to enable or disable the detection of lost jobs. */
        private const val LOST_JOBS_ENABLED_PROPERTY = "enableLostJobs"

        /**
         * Create a [MonitorConfig] from the settings stored in the given [configManager].
         */
        fun create(configManager: ConfigManager): MonitorConfig {
            val config = configManager.subConfig(Path(CONFIG_PREFIX))

            return MonitorConfig(
                namespace = config.getString(NAMESPACE_PROPERTY),
                reaperInterval = config.getInt(REAPER_INTERVAL_PROPERTY).seconds,
                reaperMaxAge = config.getInt(REAPER_MAX_AGE_PROPERTY).seconds,
                lostJobsInterval = config.getInt(LOST_JOBS_INTERVAL_PROPERTY).seconds,
                lostJobsMinAge = config.getInt(LOST_JOBS_MIN_AGE_PROPERTY).seconds,
                recentlyProcessedInterval = config.getInt(RECENTLY_PROCESSED_INTERVAL_PROPERTY).seconds,
                watchingEnabled = config.getBoolean(WATCHING_ENABLED_PROPERTY),
                reaperEnabled = config.getBoolean(REAPER_ENABLED_PROPERTY),
                lostJobsEnabled = config.getBoolean(LOST_JOBS_ENABLED_PROPERTY)
            )
        }
    }
}
