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

package org.ossreviewtoolkit.server.core.api

import io.kotest.core.test.TestCase
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.ossreviewtoolkit.server.api.v1.AnalyzerJob
import org.ossreviewtoolkit.server.api.v1.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.api.v1.AnalyzerJobStatus
import org.ossreviewtoolkit.server.api.v1.CreateOrtRun
import org.ossreviewtoolkit.server.api.v1.JobConfigurations
import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.core.testutils.basicTestAuth
import org.ossreviewtoolkit.server.core.testutils.noDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication
import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoOrganizationRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoProductRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.OrganizationRepository
import org.ossreviewtoolkit.server.model.repositories.ProductRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

private const val REPOSITORY_URL = "https://example.com/repo.git"
private const val REPOSITORY_REVISION = "revision"

class JobsRouteIntegrationTest : DatabaseTest() {
    private lateinit var analyzerJobRepository: AnalyzerJobRepository
    private lateinit var organizationRepository: OrganizationRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var repositoryRepository: RepositoryRepository

    private var orgId = -1L
    private var productId = -1L
    private var repositoryId = -1L

    private val jobConfigurations = JobConfigurations(analyzer = AnalyzerJobConfiguration(allowDynamicVersions = true))

    override suspend fun beforeTest(testCase: TestCase) {
        dataSource.connect()

        analyzerJobRepository = DaoAnalyzerJobRepository()
        organizationRepository = DaoOrganizationRepository()
        productRepository = DaoProductRepository()
        repositoryRepository = DaoRepositoryRepository()

        orgId = organizationRepository.create(name = "name", description = "description").id
        productId = productRepository.create(name = "name", description = "description", organizationId = orgId).id
        repositoryId = repositoryRepository.create(
            type = RepositoryType.GIT,
            url = REPOSITORY_URL,
            productId = productId
        ).id
    }

    init {
        test("POST /jobs/analyzer/{id}/fail should update job status") {
            ortServerTestApplication(noDbConfig) {
                val client = createJsonClient()

                client.post("/api/v1/repositories/$repositoryId/runs") {
                    headers {
                        basicTestAuth()
                    }
                    setBody(CreateOrtRun(revision = REPOSITORY_REVISION, jobs = jobConfigurations))
                }

                val analyzerJob = client.post("/api/v1/jobs/analyzer/start") {
                    headers {
                        basicTestAuth()
                    }
                }.body<AnalyzerJob>()

                val response = client.post("/api/v1/jobs/analyzer/${analyzerJob.id}/fail") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    with(body<AnalyzerJob>()) {
                        id shouldBe 1
                        startedAt shouldNot beNull()
                        finishedAt should beNull()
                        configuration shouldBe jobConfigurations.analyzer
                        status shouldBe AnalyzerJobStatus.FAILED
                        repositoryRevision shouldBe REPOSITORY_REVISION
                        repositoryUrl shouldBe REPOSITORY_URL
                    }
                }
            }
        }
    }
}
