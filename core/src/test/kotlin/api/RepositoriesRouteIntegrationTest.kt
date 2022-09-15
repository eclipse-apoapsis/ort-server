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
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication

import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.core.testutils.basicTestAuth
import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.repositories.OrganizationsRepository
import org.ossreviewtoolkit.server.dao.repositories.ProductsRepository
import org.ossreviewtoolkit.server.dao.repositories.RepositoriesRepository
import org.ossreviewtoolkit.server.shared.models.api.CreateOrganization
import org.ossreviewtoolkit.server.shared.models.api.CreateProduct
import org.ossreviewtoolkit.server.shared.models.api.CreateRepository
import org.ossreviewtoolkit.server.shared.models.api.Repository
import org.ossreviewtoolkit.server.shared.models.api.RepositoryType
import org.ossreviewtoolkit.server.shared.models.api.UpdateRepository
import org.ossreviewtoolkit.server.shared.models.api.common.OptionalValue
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

class RepositoriesRouteIntegrationTest : DatabaseTest() {
    var orgId = -1L
    var productId = -1L

    override suspend fun beforeTest(testCase: TestCase) {
        dataSource.connect()

        orgId = OrganizationsRepository.createOrganization(
            CreateOrganization(name = "org", description = "org description")
        ).id

        productId = ProductsRepository.createProduct(
            orgId,
            CreateProduct(name = "product", description = "product description")
        ).id
    }

    init {
        test("GET /repositories/{repositoryId} should return a single repository") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }
                val client = createJsonClient()

                val repository = CreateRepository(RepositoryType.GIT, "https://example.com/repo.git")

                val createdRepository = RepositoriesRepository.createRepository(productId, repository)
                val response = client.get("/api/v1/repositories/${createdRepository.id}") {
                    headers {
                        basicTestAuth()
                    }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Repository>() shouldBe Repository(createdRepository.id, repository.type, repository.url)
                }
            }
        }

        test("PATCH /repositories/{repositoryId} should update a repository") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }
                val client = createJsonClient()

                val repository = CreateRepository(RepositoryType.GIT, "https://example.com/repo.git")
                val createdRepository = RepositoriesRepository.createRepository(productId, repository)
                val updateRepository = UpdateRepository(
                    OptionalValue.Present(RepositoryType.SUBVERSION),
                    OptionalValue.Present("https://svn.example.com/repos/org/repo/trunk")
                )

                val response = client.patch("/api/v1/repositories/${createdRepository.id}") {
                    headers {
                        basicTestAuth()
                    }
                    setBody(updateRepository)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Repository>() shouldBe Repository(
                        createdRepository.id,
                        (updateRepository.type as OptionalValue.Present).value,
                        (updateRepository.url as OptionalValue.Present).value
                    )
                }
            }
        }

        test("DELETE /repositories/{repositoryId} should delete a repository") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }
                val client = createJsonClient()

                val repository = CreateRepository(RepositoryType.GIT, "https://example.com/repo.git")
                val createdRepository = RepositoriesRepository.createRepository(productId, repository)

                val response = client.delete("/api/v1/repositories/${createdRepository.id}") {
                    headers {
                        basicTestAuth()
                    }
                }

                response.status shouldBe HttpStatusCode.NoContent
                RepositoriesRepository.listRepositoriesForProduct(productId) shouldBe emptyList()
            }
        }
    }
}
