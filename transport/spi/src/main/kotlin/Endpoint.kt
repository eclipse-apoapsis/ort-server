/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.transport

import kotlin.reflect.KClass

import org.eclipse.apoapsis.ortserver.model.orchestrator.AdvisorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.AnalyzerRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ConfigRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.EvaluatorRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.NotifierRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.model.orchestrator.ReporterRequest
import org.eclipse.apoapsis.ortserver.model.orchestrator.ScannerRequest

/**
 * A type describing the different endpoints supported by the ORT server.
 *
 * This type and its concrete subtypes are used to define the targets for the internal communication between the
 * Orchestrator component and the different workers. Via the properties defined by the single members of this
 * hierarchy, type-safe serialization of messages is possible.
 */
sealed class Endpoint<T : Any>(
    /**
     * The class of messages this endpoint can handle. Based on this property, a proper serialization infrastructure
     * can be set up.
     */
    val messageClass: KClass<T>,

    /**
     * A prefix used for configuration properties related to this endpoint. This is used to configure concrete
     * implementations of message senders and receivers that need to interact with this endpoint.
     */
    val configPrefix: String
) {
    companion object {
        /**
         * Return the [Endpoint] instance for the given configuration [prefix] or throw an exception if the prefix is
         * invalid.
         */
        fun fromConfigPrefix(prefix: String): Endpoint<*> = when (prefix) {
            OrchestratorEndpoint.configPrefix -> OrchestratorEndpoint
            ConfigEndpoint.configPrefix -> ConfigEndpoint
            AnalyzerEndpoint.configPrefix -> AnalyzerEndpoint
            AdvisorEndpoint.configPrefix -> AdvisorEndpoint
            ScannerEndpoint.configPrefix -> ScannerEndpoint
            EvaluatorEndpoint.configPrefix -> EvaluatorEndpoint
            NotifierEndpoint.configPrefix -> NotifierEndpoint
            ReporterEndpoint.configPrefix -> ReporterEndpoint
            else -> throw IllegalArgumentException("Unknown endpoint configuration prefix: $prefix")
        }
    }
}

/**
 * A concrete [Endpoint] declaration representing the Orchestrator endpoint.
 */
data object OrchestratorEndpoint : Endpoint<OrchestratorMessage>(OrchestratorMessage::class, "orchestrator")

/**
 * A concrete [Endpoint] declaration representing the Config endpoint.
 */
data object ConfigEndpoint : Endpoint<ConfigRequest>(ConfigRequest::class, "config")

/**
 * A concrete [Endpoint] declaration representing the Analyzer worker.
 */
data object AnalyzerEndpoint : Endpoint<AnalyzerRequest>(AnalyzerRequest::class, "analyzer")

/**
 * A concrete [Endpoint] declaration representing the Advisor worker.
 */
data object AdvisorEndpoint : Endpoint<AdvisorRequest>(AdvisorRequest::class, "advisor")

/**
 * A concrete [Endpoint] declaration representing the Scanner worker.
 */
data object ScannerEndpoint : Endpoint<ScannerRequest>(ScannerRequest::class, "scanner")

/**
 * A concrete [Endpoint] declaration representing the Evaluator worker.
 */
data object EvaluatorEndpoint : Endpoint<EvaluatorRequest>(EvaluatorRequest::class, "evaluator")

/**
 * A concrete [Endpoint] declaration representing the Notifier worker.
 */
data object NotifierEndpoint : Endpoint<NotifierRequest>(NotifierRequest::class, "notifier")

/**
 * A concrete [Endpoint] declaration representing the Reporter worker.
 */
data object ReporterEndpoint : Endpoint<ReporterRequest>(ReporterRequest::class, "reporter")
