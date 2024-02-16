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

package org.eclipse.apoapsis.ortserver.core.utils

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get

import org.eclipse.apoapsis.ortserver.core.createJsonClient
import org.eclipse.apoapsis.ortserver.core.testutils.noDbConfig
import org.eclipse.apoapsis.ortserver.core.testutils.ortServerTestApplication
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.utils.test.Integration

class ExtensionsTest : WordSpec({
    tags(Integration)

    "ApplicationCall.listQueryParameters" should {
        "handle a request without parameters" {
            testParameterExtraction(null) {
                it.limit shouldBe DEFAULT_LIMIT
                it.offset shouldBe 0
                it.sortFields shouldBe listOf(OrderField("name", OrderDirection.ASCENDING))
            }
        }

        "handle a request with a limit parameter" {
            val limit = 42
            testParameterExtraction("?limit=$limit") { params ->
                params.limit shouldBe limit
                params.offset shouldBe 0
            }
        }

        "handle a request with an offset parameter" {
            val offset = 128
            testParameterExtraction("?offset=$offset") { params ->
                params.limit shouldBe DEFAULT_LIMIT
                params.offset shouldBe offset
            }
        }

        "handle a request with a sort parameter defining a field" {
            val field = "name"
            testParameterExtraction("?sort=name") { params ->
                params.sortFields shouldContainExactly listOf(OrderField(field, OrderDirection.ASCENDING))
                params.limit shouldBe DEFAULT_LIMIT
                params.offset shouldBe 0
            }
        }

        "handle a request with a sort parameter defining multiple fields" {
            val fields = listOf("lastName", "firstName", "birthDate")
            val expectedOrderFields = fields.map { name -> OrderField(name, OrderDirection.ASCENDING) }
            val query = "?sort=${fields.joinToString(",")}"

            testParameterExtraction(query) { params ->
                params.sortFields shouldContainExactly expectedOrderFields
                params.limit shouldBe DEFAULT_LIMIT
                params.offset shouldBe 0
            }
        }

        "handle a request with a sort parameter defining a field with an ascending prefix" {
            val field = "fieldToSort"
            testParameterExtraction("?sort=%2B$field") { params ->
                params.sortFields shouldContainExactly listOf(OrderField(field, OrderDirection.ASCENDING))
                params.limit shouldBe DEFAULT_LIMIT
                params.offset shouldBe 0
            }
        }

        "handle a request with a sort parameter defining a field with a descending prefix" {
            val field = "creationDate"
            testParameterExtraction("?sort=-$field") { params ->
                params.sortFields shouldContainExactly listOf(OrderField(field, OrderDirection.DESCENDING))
                params.limit shouldBe DEFAULT_LIMIT
                params.offset shouldBe 0
            }
        }
    }
})

/**
 * Execute a test for extracting the parameters from the given [query] by applying the specified [check] function.
 */
private fun testParameterExtraction(query: String?, check: (ListQueryParameters) -> Unit) {
    ortServerTestApplication(config = noDbConfig) {
        routing {
            get("/test") {
                val parameters = call.listQueryParameters(OrderField("name", OrderDirection.ASCENDING))

                check(parameters)

                call.respond("SUCCESS")
            }
        }

        val client = createJsonClient()

        val uri = "/test${query.orEmpty()}"
        client.get(uri) shouldHaveStatus HttpStatusCode.OK
    }
}
