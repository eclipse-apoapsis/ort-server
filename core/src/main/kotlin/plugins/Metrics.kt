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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.metrics.micrometer.MicrometerMetrics

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.util.HierarchicalNameMapper
import io.micrometer.graphite.GraphiteConfig
import io.micrometer.graphite.GraphiteMeterRegistry

import org.eclipse.apoapsis.ortserver.core.utils.JobMetrics

import org.koin.ktor.ext.inject

fun Application.configureMetrics() {
    val config: ApplicationConfig by inject()
    val metricsPrefix = config.property("micrometer.graphite.tagsAsPrefix").getString()

    // Create and configure the Graphite registry
    val graphiteConfig = GraphiteConfig { key -> config.propertyOrNull("micrometer.$key")?.getString() }
    val graphiteRegistry = GraphiteMeterRegistry(graphiteConfig, Clock.SYSTEM) { id, convention ->
        "$metricsPrefix." + HierarchicalNameMapper.DEFAULT.toHierarchicalName(id, convention)
    }

    // Install MicrometerMetrics feature with the Graphite registry
    install(MicrometerMetrics) {
        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            JvmThreadMetrics(),
            ProcessorMetrics(),
            JobMetrics(this@configureMetrics)
        )
        registry = graphiteRegistry
    }
}
