/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.transport

import kotlin.reflect.KClass

import org.ossreviewtoolkit.server.model.orchestrator.AdvisorRequest
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerRequest
import org.ossreviewtoolkit.server.model.orchestrator.OrchestratorMessage
import org.ossreviewtoolkit.server.model.orchestrator.ScannerRequest

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
)

/**
 * A concrete [Endpoint] declaration representing the Orchestrator endpoint.
 */
object OrchestratorEndpoint : Endpoint<OrchestratorMessage>(OrchestratorMessage::class, "orchestrator")

/**
 * A concrete [Endpoint] declaration representing the Analyzer worker.
 */
object AnalyzerEndpoint : Endpoint<AnalyzerRequest>(AnalyzerRequest::class, "analyzer")

/**
 * A concrete [Endpoint] declaration representing the Advisor worker.
 */
object AdvisorEndpoint : Endpoint<AdvisorRequest>(AdvisorRequest::class, "advisor")

/**
 * A concrete [Endpoint] declaration representing the Scanner worker.
 */
object ScannerEndpoint : Endpoint<ScannerRequest>(ScannerRequest::class, "scanner")
