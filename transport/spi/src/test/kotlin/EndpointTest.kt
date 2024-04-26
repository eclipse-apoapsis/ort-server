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

package org.eclipse.apoapsis.ortserver.transport

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class EndpointTest : WordSpec({
    "fromConfigPrefix" should {
        "return the correct endpoint for the given prefix" {
            Endpoint.fromConfigPrefix("advisor") shouldBe AdvisorEndpoint
            Endpoint.fromConfigPrefix("analyzer") shouldBe AnalyzerEndpoint
            Endpoint.fromConfigPrefix("evaluator") shouldBe EvaluatorEndpoint
            Endpoint.fromConfigPrefix("notifier") shouldBe NotifierEndpoint
            Endpoint.fromConfigPrefix("reporter") shouldBe ReporterEndpoint
            Endpoint.fromConfigPrefix("scanner") shouldBe ScannerEndpoint
        }

        "throw an IllegalArgumentException for an unknown prefix" {
            val invalidPrefix = "unknown"

            val exception = shouldThrow<IllegalArgumentException> {
                Endpoint.fromConfigPrefix(invalidPrefix)
            }

            exception.message shouldContain invalidPrefix
        }
    }
})
