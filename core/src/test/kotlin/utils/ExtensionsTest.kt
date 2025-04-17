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
import io.ktor.server.response.respond
import io.ktor.server.routing.get

import org.eclipse.apoapsis.ortserver.api.v1.model.PagingOptions
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty
import org.eclipse.apoapsis.ortserver.core.createJsonClient
import org.eclipse.apoapsis.ortserver.core.testutils.noDbConfig
import org.eclipse.apoapsis.ortserver.core.testutils.ortServerTestApplication
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.utils.test.Integration

class ExtensionsTest : WordSpec({
    tags(Integration)

    "ApplicationCall.listQueryParameters" should {
        "handle a request without parameters" {
            testPagingOptionsExtraction(null) {
                limit shouldBe DEFAULT_LIMIT
                offset shouldBe 0
                sortProperties shouldBe listOf(SortProperty("name", SortDirection.ASCENDING))
            }
        }

        "handle a request with a limit parameter" {
            val limit = 42
            testPagingOptionsExtraction("?limit=$limit") {
                limit shouldBe limit
                offset shouldBe 0
            }
        }

        "handle a request with an offset parameter" {
            val offset = 128
            testPagingOptionsExtraction("?offset=$offset") {
                limit shouldBe DEFAULT_LIMIT
                offset shouldBe offset
            }
        }

        "handle a request with a sort parameter defining a field" {
            val field = "name"
            testPagingOptionsExtraction("?sort=name") {
                limit shouldBe DEFAULT_LIMIT
                offset shouldBe 0
                sortProperties shouldContainExactly listOf(SortProperty(field, SortDirection.ASCENDING))
            }
        }

        "handle a request with a sort parameter defining multiple fields" {
            val fields = listOf("lastName", "firstName", "birthDate")
            val expectedSortProperties = fields.map { name -> SortProperty(name, SortDirection.ASCENDING) }
            val query = "?sort=${fields.joinToString(",")}"

            testPagingOptionsExtraction(query) {
                limit shouldBe DEFAULT_LIMIT
                offset shouldBe 0
                sortProperties shouldContainExactly expectedSortProperties
            }
        }

        "handle a request with a sort parameter defining a field with an ascending prefix" {
            val field = "fieldToSort"
            testPagingOptionsExtraction("?sort=%2B$field") {
                limit shouldBe DEFAULT_LIMIT
                offset shouldBe 0
                sortProperties shouldContainExactly listOf(SortProperty(field, SortDirection.ASCENDING))
            }
        }

        "handle a request with a sort parameter defining a field with a descending prefix" {
            val field = "creationDate"
            testPagingOptionsExtraction("?sort=-$field") {
                limit shouldBe DEFAULT_LIMIT
                offset shouldBe 0
                sortProperties shouldContainExactly listOf(SortProperty(field, SortDirection.DESCENDING))
            }
        }
    }
})

/**
 * Execute a test for extracting the [PagingOptions] from the given [query] by applying the specified [check] function.
 */
private fun testPagingOptionsExtraction(query: String?, check: PagingOptions.() -> Unit) {
    ortServerTestApplication(config = noDbConfig) {
        routing {
            get("/test") {
                val pagingOptions = call.pagingOptions(SortProperty("name", SortDirection.ASCENDING))

                check(pagingOptions)

                call.respond("SUCCESS")
            }
        }

        val client = createJsonClient()

        val uri = "/test${query.orEmpty()}"
        client.get(uri) shouldHaveStatus HttpStatusCode.OK
    }
}
