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

package org.eclipse.apoapsis.ortserver.core.api

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.ktor.client.haveHeader
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import kotlin.time.Duration.Companion.minutes

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.services.DefaultAuthorizationService
import org.eclipse.apoapsis.ortserver.services.OrganizationService
import org.eclipse.apoapsis.ortserver.services.ProductService
import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.utils.test.Integration

class DownloadsRouteIntegrationTest : AbstractAuthorizationTest({
    tags(Integration)

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
            dbExtension.fixtures.ortRunRepository,
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
    val reportToken = "secret-token-to-access-the-test-report"

    /**
     * Create an [OrtRun], store a report for the created run, and return the created run.
     */
    suspend fun createReport(): OrtRun {
        val run = dbExtension.fixtures.createOrtRun(repositoryId)
        val key = Key("${run.id}|$reportFile")

        val storage = Storage.create("reportStorage", ConfigManager.create(ConfigFactory.load("application-test.conf")))
        storage.write(key, reportData, "application/pdf")

        val reporterJob = dbExtension.fixtures.createReporterJob(ortRunId = run.id)
        val downloadLink = "https://report.example.org/download/api/v1/runs/${run.id}/downloads/report/$reportToken"
        val report = Report(reportFile, downloadLink, Clock.System.now() + 60.minutes)
        dbExtension.fixtures.reporterRunRepository.create(
            reporterJob.id,
            Clock.System.now() - 1.minutes,
            Clock.System.now(),
            listOf(report)
        )

        return run
    }

    "GET /runs/{runId}/download/report/{token}" should {
        "allow downloading a report by token" {
            integrationTestApplication {
                val run = createReport()

                val response = superuserClient.get("/api/v1/runs/${run.id}/downloads/report/$reportToken")

                response shouldHaveStatus HttpStatusCode.OK
                response should haveHeader("Content-Type", "application/pdf")
                response.body<ByteArray>() shouldBe reportData
            }
        }

        "handle an invalid token" {
            integrationTestApplication {
                val run = createReport()

                val response = superuserClient.get("/api/v1/runs/${run.id}/downloads/report/invalidToken")

                response shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "not require any permission" {
            integrationTestApplication {
                val run = createReport()

                val response = unauthenticatedClient.get("/api/v1/runs/${run.id}/downloads/report/$reportToken")

                response shouldHaveStatus HttpStatusCode.OK
                response should haveHeader("Content-Type", "application/pdf")
                response.body<ByteArray>() shouldBe reportData
            }
        }
    }
})
