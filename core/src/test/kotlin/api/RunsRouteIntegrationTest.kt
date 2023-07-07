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

import io.kotest.assertions.ktor.client.haveHeader
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.authorization.RepositoryPermission
import org.ossreviewtoolkit.server.services.DefaultAuthorizationService
import org.ossreviewtoolkit.server.services.OrganizationService
import org.ossreviewtoolkit.server.services.ProductService
import org.ossreviewtoolkit.server.storage.Key
import org.ossreviewtoolkit.server.storage.Storage

class RunsRouteIntegrationTest : AbstractIntegrationTest({
    var repositoryId = -1L

    beforeEach {
        val authorizationService = DefaultAuthorizationService(
            keycloakClient,
            dbExtension.db,
            dbExtension.fixtures.organizationRepository,
            dbExtension.fixtures.productRepository,
            dbExtension.fixtures.repositoryRepository,
            keycloakGroupPrefix = ""
        )

        val organizationService = OrganizationService(
            dbExtension.db,
            dbExtension.fixtures.organizationRepository,
            dbExtension.fixtures.productRepository,
            authorizationService
        )

        val productService = ProductService(
            dbExtension.db,
            dbExtension.fixtures.productRepository,
            dbExtension.fixtures.repositoryRepository,
            authorizationService
        )

        val orgId = organizationService.createOrganization(name = "name", description = "description").id
        val productId =
            organizationService.createProduct(name = "name", description = "description", organizationId = orgId).id
        repositoryId = productService.createRepository(
            type = RepositoryType.GIT,
            url = "https://example.org/repo.git",
            productId = productId
        ).id
    }

    val reportFile = "disclosure-document-pdf"
    val reportData = "Data of the report to download".toByteArray()

    /**
     * Create an [OrtRun], store a report for the created run, and return the created run.
     */
    fun createReport(): OrtRun {
        val run = dbExtension.fixtures.createOrtRun(repositoryId)
        val key = Key("${run.id}|$reportFile")

        val storage = Storage.create("reportStorage", ConfigFactory.load("application-test.conf"))
        storage.write(key, reportData, "application/pdf")

        return run
    }

    "GET /runs/{runId}/report/{fileName}" should {
        "download a report" {
            integrationTestApplication {
                val run = createReport()

                val response = superuserClient.get("/api/v1/runs/${run.id}/reporter/$reportFile")

                response shouldHaveStatus HttpStatusCode.OK
                response should haveHeader("Content-Type", "application/pdf")
                response.body<ByteArray>() shouldBe reportData
            }
        }

        "handle a missing report" {
            integrationTestApplication {
                val missingReportFile = "nonExistingReport.pdf"
                val run = dbExtension.fixtures.createOrtRun(repositoryId)

                val response = superuserClient.get("/api/v1/runs/${run.id}/reporter/$missingReportFile")

                response shouldHaveStatus HttpStatusCode.NotFound
                response.body<ErrorResponse>().cause shouldContain missingReportFile
            }
        }

        "require RepositoryPermission.READ_ORT_RUNS" {
            val run = createReport()

            requestShouldRequireRole(RepositoryPermission.READ_ORT_RUNS.roleName(repositoryId)) {
                get("/api/v1/runs/${run.id}/reporter/$reportFile")
            }
        }
    }
})
