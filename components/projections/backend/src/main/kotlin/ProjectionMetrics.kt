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

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder
import io.micrometer.core.instrument.composite.CompositeMeterRegistry

import java.util.concurrent.atomic.AtomicLong

import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** The outcome of a build of a projection. */
internal enum class BuildOutcome {
    /** The projection was built successfully. */
    READY,

    /** The build failed with an exception. */
    FAILED,

    /** The build was discarded because the lease on it was lost. */
    LEASE_LOST
}

/**
 * The metrics for building projections of the given [types]. Meters are recorded in an internal registry, which
 * publishes them to all registries this binder is [bound][bindTo] to.
 *
 * The following meters are provided, each with a `type` tag for the projection type:
 * - `projections.builds`: A timer for the duration of builds, with an `outcome` tag.
 * - `projections.requests`: A timer for the time requests waited for builds, with a `result` tag.
 * - `projections.status`: A gauge for the number of projections in the registry, with a `status` tag.
 */
class ProjectionMetrics internal constructor(types: Set<String>) : MeterBinder {
    private val meterRegistry = CompositeMeterRegistry()

    private val statusCounts = types.flatMap { type ->
        ProjectionStatus.entries.map { status -> type to status }
    }.associateWith { AtomicLong(0) }

    init {
        statusCounts.forEach { (typeAndStatus, count) ->
            Gauge.builder("projections.status", count) { it.toDouble() }
                .description("The number of projections with the given status.")
                .tag("type", typeAndStatus.first)
                .tag("status", typeAndStatus.second.name.lowercase())
                .register(meterRegistry)
        }
    }

    override fun bindTo(registry: MeterRegistry) {
        meterRegistry.add(registry)
    }

    /** Record a build of a projection of the given [type] that took [duration] and ended with the given [outcome]. */
    internal fun recordBuild(type: String, outcome: BuildOutcome, duration: Duration) {
        Timer.builder("projections.builds")
            .description("The duration of builds of projections.")
            .tag("type", type)
            .tag("outcome", outcome.name.lowercase())
            .register(meterRegistry)
            .record(duration.toJavaDuration())
    }

    /** Record a request for a projection of the given [type] that took [duration] and returned the given [result]. */
    internal fun recordRequest(type: String, result: ProjectionResult, duration: Duration) {
        val resultTag = when (result) {
            ProjectionResult.Ready -> "ready"
            ProjectionResult.Building -> "building"
            is ProjectionResult.Failed -> "failed"
        }

        Timer.builder("projections.requests")
            .description("The duration of requests for projections, including the time waited for builds.")
            .tag("type", type)
            .tag("result", resultTag)
            .register(meterRegistry)
            .record(duration.toJavaDuration())
    }

    /** Update the numbers of projections for each type and status with the given [counts]. */
    internal fun updateStatusCounts(counts: Map<Pair<String, ProjectionStatus>, Long>) {
        statusCounts.forEach { (typeAndStatus, count) -> count.set(counts[typeAndStatus] ?: 0) }
    }
}
