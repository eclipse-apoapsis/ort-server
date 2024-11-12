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

package org.eclipse.apoapsis.ortserver.kubernetes.jobmonitor

import java.time.OffsetDateTime
import java.time.ZoneOffset

import kotlin.time.Duration

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * An internally used helper class that provides some functionality related to querying the current time and
 * computing thresholds for filtering jobs based on their age.
 */
internal class TimeHelper(
    /** The clock to determine the current time and the age of jobs. */
    private val clock: Clock = Clock.System,

    /** The default zone offset for this system. */
    private val systemZoneOffset: ZoneOffset = OffsetDateTime.now().offset
) {
    /**
     * Return the current time as an [Instant].
     */
    fun now(): Instant = clock.now()

    /**
     * Return a timestamp that lies the given [duration] before the current time as an [OffsetDateTime]. This format
     * is used by the Kubernetes API; so it can be used directly to compare against the timestamps of jobs.
     */
    fun before(duration: Duration): OffsetDateTime =
        java.time.Instant.ofEpochSecond(clock.now().minus(duration).epochSeconds).atOffset(systemZoneOffset)
}
