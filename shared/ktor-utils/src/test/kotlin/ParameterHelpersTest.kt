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

package org.eclipse.apoapsis.ortserver.shared.ktorutils

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication

import org.eclipse.apoapsis.ortserver.api.v1.model.ComparisonOperator
import org.eclipse.apoapsis.ortserver.api.v1.model.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.utils.test.Integration

class ParameterHelpersTest : WordSpec({
    tags(Integration)

    "ApplicationCall.stringSetFilter" should {
        "return null when the parameter is absent" {
            testStringSetFilter(null, null)
        }

        "include comma-separated values" {
            testStringSetFilter(
                "?filter=MIT,Apache-2.0",
                FilterOperatorAndValue(ComparisonOperator.IN, setOf("MIT", "Apache-2.0"))
            )
        }

        "exclude values when the parameter contains a minus" {
            testStringSetFilter(
                "?filter=-,MIT,Apache-2.0",
                FilterOperatorAndValue(ComparisonOperator.NOT_IN, setOf("MIT", "Apache-2.0"))
            )
        }

        "deduplicate values" {
            testStringSetFilter(
                "?filter=MIT,Apache-2.0,MIT",
                FilterOperatorAndValue(ComparisonOperator.IN, setOf("MIT", "Apache-2.0"))
            )
        }
    }
})

private fun testStringSetFilter(query: String?, expected: FilterOperatorAndValue<Set<String>>?) {
    testApplication {
        routing {
            get("/test") {
                call.stringSetFilter("filter") shouldBe expected
                call.respond(HttpStatusCode.OK)
            }
        }

        client.get("/test${query.orEmpty()}") shouldHaveStatus HttpStatusCode.OK
    }
}
