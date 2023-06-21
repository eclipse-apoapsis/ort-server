/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.api

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpStatusCode

import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.core.testutils.basicTestAuth
import org.ossreviewtoolkit.server.core.testutils.noDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.storage.Key
import org.ossreviewtoolkit.server.storage.Storage

class RunsRouteIntegrationTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    "GET /runs/{runId}/report/{fileName}" should {
        "download a report" {
            ortServerTestApplication(dbExtension.db, noDbConfig) {
                val run = dbExtension.fixtures.createOrtRun()
                val reportFile = "disclosure-document-pdf"
                val reportData = "Data of the report to download".toByteArray()
                val key = Key("${run.id}|$reportFile")

                val storage = Storage.create("reportStorage", ConfigFactory.load("application-test.conf"))
                storage.write(key, reportData, "application/pdf")

                val client = createJsonClient()

                val response = client.get("/api/v1/runs/${run.id}/reporter/$reportFile") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    headers["Content-Type"] shouldBe "application/pdf"
                    body<ByteArray>() shouldBe reportData
                }
            }
        }

        "handle a missing report" {
            ortServerTestApplication(dbExtension.db, noDbConfig) {
                val reportFile = "nonExistingReport.pdf"
                val run = dbExtension.fixtures.createOrtRun()
                val client = createJsonClient()

                val response = client.get("/api/v1/runs/${run.id}/reporter/$reportFile") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.NotFound
                    val responseBody = body<ErrorResponse>()
                    responseBody.cause shouldContain reportFile
                }
            }
        }
    }
})
