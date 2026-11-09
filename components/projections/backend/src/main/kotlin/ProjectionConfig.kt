/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.projections

import com.typesafe.config.Config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

import org.eclipse.apoapsis.ortserver.utils.config.getIntOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getLongOrDefault

/** The configuration for building projections. */
data class ProjectionConfig(
    /** The duration of a lease on a build. It is renewed regularly while the build is running. */
    val leaseDuration: Duration = 5.minutes,

    /** The maximum number of builds running in parallel in one instance. */
    val maxParallelBuilds: Int = 2,

    /** The interval in which pending builds, builds with expired leases, and failed builds are claimed. */
    val recoveryInterval: Duration = 30.seconds,

    /** The maximum number of attempts to build a projection before giving up until it is requested again. */
    val maxAttempts: Int = 3,

    /** The delay after which a failed build is retried. */
    val retryDelay: Duration = 1.minutes,

    /**
     * The maximum time a request that accepts an asynchronous response waits for the build of a projection to finish
     * before it is answered that the projection is still being built. This allows to answer requests for small
     * projections right away.
     */
    val asyncRequestWait: Duration = 2.seconds,

    /**
     * The maximum time a request that does not accept an asynchronous response waits for the build of a projection to
     * finish. It should be shorter than the timeouts of proxies in front of the server.
     */
    val syncRequestWait: Duration = 30.seconds
) {
    /** Validate this configuration and throw an [IllegalArgumentException] if a value is invalid. */
    fun validate() {
        require(leaseDuration.isPositive() && leaseDuration.isFinite()) {
            "The lease duration must be positive, but is $leaseDuration."
        }

        require(maxParallelBuilds > 0) {
            "The maximum number of parallel builds must be positive, but is $maxParallelBuilds."
        }

        require(recoveryInterval.isPositive() && recoveryInterval.isFinite()) {
            "The recovery interval must be positive, but is $recoveryInterval."
        }

        require(maxAttempts > 0) { "The maximum number of attempts must be positive, but is $maxAttempts." }
        require(!retryDelay.isNegative()) { "The retry delay must not be negative, but is $retryDelay." }

        require(!asyncRequestWait.isNegative()) {
            "The wait time for asynchronous requests must not be negative, but is $asyncRequestWait."
        }

        require(!syncRequestWait.isNegative()) {
            "The wait time for synchronous requests must not be negative, but is $syncRequestWait."
        }
    }

    companion object {
        /** The name of the configuration section for projections. */
        const val SECTION = "projections"

        /**
         * Create a [ProjectionConfig] from the `projections` section of the given [config]. Missing properties are
         * set to their default values. Throw an [IllegalArgumentException] if a configured value is invalid.
         */
        fun create(config: Config): ProjectionConfig {
            val defaults = ProjectionConfig()

            return ProjectionConfig(
                leaseDuration = config.getLongOrDefault(
                    "$SECTION.leaseDurationSeconds",
                    defaults.leaseDuration.inWholeSeconds
                ).seconds,
                maxParallelBuilds = config.getIntOrDefault("$SECTION.maxParallelBuilds", defaults.maxParallelBuilds),
                recoveryInterval = config.getLongOrDefault(
                    "$SECTION.recoveryIntervalSeconds",
                    defaults.recoveryInterval.inWholeSeconds
                ).seconds,
                maxAttempts = config.getIntOrDefault("$SECTION.maxAttempts", defaults.maxAttempts),
                retryDelay = config.getLongOrDefault(
                    "$SECTION.retryDelaySeconds",
                    defaults.retryDelay.inWholeSeconds
                ).seconds,
                asyncRequestWait = config.getLongOrDefault(
                    "$SECTION.asyncRequestWaitMillis",
                    defaults.asyncRequestWait.inWholeMilliseconds
                ).milliseconds,
                syncRequestWait = config.getLongOrDefault(
                    "$SECTION.syncRequestWaitMillis",
                    defaults.syncRequestWait.inWholeMilliseconds
                ).milliseconds
            ).apply { validate() }
        }
    }
}
