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

import io.kotest.core.extensions.install
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.ossreviewtoolkit.server.api.v1.OrtRun
import org.ossreviewtoolkit.server.api.v1.Repository
import org.ossreviewtoolkit.server.api.v1.RepositoryType as ApiRepositoryType
import org.ossreviewtoolkit.server.api.v1.UpdateRepository
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.clients.keycloak.test.KeycloakTestExtension
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakClientForTestRealm
import org.ossreviewtoolkit.server.clients.keycloak.test.createKeycloakConfigMapForTestRealm
import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.core.testutils.basicTestAuth
import org.ossreviewtoolkit.server.core.testutils.noDbConfig
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.authorization.RepositoryPermission
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.model.util.asPresent

class RepositoriesRouteIntegrationTest : StringSpec() {
    private val dbExtension: DatabaseTestExtension = extension(DatabaseTestExtension())
    private val keycloak = install(KeycloakTestExtension(createRealmPerTest = true))
    private val keycloakConfig = keycloak.createKeycloakConfigMapForTestRealm()
    private val keycloakClient = keycloak.createKeycloakClientForTestRealm()

    private lateinit var ortRunRepository: OrtRunRepository
    private lateinit var repositoryRepository: RepositoryRepository

    private var orgId = -1L
    private var productId = -1L

    init {
        beforeEach {
            ortRunRepository = dbExtension.fixtures.ortRunRepository
            repositoryRepository = dbExtension.fixtures.repositoryRepository

            orgId = dbExtension.fixtures.organizationRepository.create(name = "name", description = "description").id
            productId = dbExtension.fixtures.productRepository
                .create(name = "name", description = "description", organizationId = orgId).id
        }

        "GET /repositories/{repositoryId} should return a single repository" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val type = RepositoryType.GIT
                val url = "https://example.com/repo.git"

                val createdRepository = repositoryRepository.create(type = type, url = url, productId = productId)

                val response = client.get("/api/v1/repositories/${createdRepository.id}") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Repository>() shouldBe Repository(createdRepository.id, type.mapToApi(), url)
                }
            }
        }

        "PATCH /repositories/{repositoryId} should update a repository" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val createdRepository = repositoryRepository.create(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                )

                val updateRepository = UpdateRepository(
                    ApiRepositoryType.SUBVERSION.asPresent(),
                    "https://svn.example.com/repos/org/repo/trunk".asPresent()
                )

                val response = client.patch("/api/v1/repositories/${createdRepository.id}") {
                    headers { basicTestAuth() }
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

        "DELETE /repositories/{repositoryId} should delete a repository" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val createdRepository = repositoryRepository.create(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                )

                val response = client.delete("/api/v1/repositories/${createdRepository.id}") {
                    headers { basicTestAuth() }
                }

                response.status shouldBe HttpStatusCode.NoContent
                repositoryRepository.listForProduct(productId) shouldBe emptyList()
            }
        }

        "DELETE /repositories/{repositoryId} should delete Keycloak roles" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val client = createJsonClient()

                val createdRepository = repositoryRepository.create(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                )

                client.delete("/api/v1/repositories/${createdRepository.id}") {
                    headers { basicTestAuth() }
                }

                keycloakClient.getRoles().map { it.name.value } shouldNot containAnyOf(
                    RepositoryPermission.getRolesForRepository(createdRepository.id)
                )
            }
        }

        "GET /repositories/{repositoryId}/runs should return the ORT runs on a repository" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val createdRepository = repositoryRepository.create(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                )

                val run1 = ortRunRepository.create(createdRepository.id, "branch-1", JobConfigurations())
                val run2 = ortRunRepository.create(createdRepository.id, "branch-2", JobConfigurations())

                val client = createJsonClient()

                val response = client.get("/api/v1/repositories/${createdRepository.id}/runs") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<OrtRun>>() shouldBe listOf(run1.mapToApi(), run2.mapToApi())
                }
            }
        }

        "GET /repositories/{repositoryId}/runs should support query parameters" {
            ortServerTestApplication(dbExtension.db, noDbConfig, keycloakConfig) {
                val createdRepository = repositoryRepository.create(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo.git",
                    productId = productId
                )

                ortRunRepository.create(createdRepository.id, "branch-1", JobConfigurations())
                val run2 = ortRunRepository.create(createdRepository.id, "branch-2", JobConfigurations())

                val client = createJsonClient()

                val query = "?sort=-revision,-createdAt&limit=1"
                val response = client.get("/api/v1/repositories/${createdRepository.id}/runs$query") {
                    headers { basicTestAuth() }
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<OrtRun>>() shouldBe listOf(run2.mapToApi())
                }
            }
        }
    }
}
