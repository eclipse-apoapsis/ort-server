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
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication

import org.ossreviewtoolkit.server.core.createJsonClient
import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.repositories.OrganizationsRepository
import org.ossreviewtoolkit.server.shared.models.api.Organization
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

class OrganizationsRouteIntegrationTest : DatabaseTest() {
    override suspend fun beforeTest(testCase: TestCase) = dataSource.connect()

    init {
        test("GET /organizations should return all existing organizations") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }

                val org1 = Organization(name = "testOrg1", description = "description of testOrg")
                val org2 = Organization(name = "testOrg2", description = "description of testOrg")

                val createdOrganization1 = OrganizationsRepository.createOrganization(org1.name, org1.description)
                val createdOrganization2 = OrganizationsRepository.createOrganization(org2.name, org2.description)

                val client = createJsonClient()

                val response = client.get("/api/v1/organizations")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<List<Organization>>() shouldBe listOf(
                        org1.copy(id = createdOrganization1.id),
                        org2.copy(id = createdOrganization2.id)
                    )
                }
            }
        }

        test("GET /organizations/{organizationId} should return a single organization") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }

                val org = Organization(name = "testOrg", description = "description of testOrg")

                val createdOrganization = OrganizationsRepository.createOrganization(org.name, org.description)

                val client = createJsonClient()

                val response = client.get("/api/v1/organizations/${createdOrganization.id}")

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Organization>() shouldBe org.copy(id = createdOrganization.id)
                }
            }
        }

        test("GET /organizations/{organizationId} should respond with NotFound if no organization exists") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }

                val client = createJsonClient()

                val response = client.get("/api/v1/organizations/999999")

                with(response) {
                    status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        test("POST /organizations should create an organization in the database") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }

                val client = createJsonClient()

                val organization = Organization(name = "testOrg", description = "description of testOrg")

                val response = client.post("/api/v1/organizations") {
                    headers { contentType(ContentType.Application.Json) }
                    setBody(organization)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Created
                    body<Organization>() shouldBe organization.copy(id = 1)
                }

                OrganizationsRepository.getOrganization(1)?.mapToApiModel() shouldBe organization.copy(id = 1)
            }
        }

        test("POST /organizations with an already existing organization should respond with CONFLICT") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }

                val org = Organization(name = "testOrg", description = "description of testOrg")
                OrganizationsRepository.createOrganization(org.name, org.description)

                val client = createJsonClient()

                val response = client.post("/api/v1/organizations") {
                    headers { contentType(ContentType.Application.Json) }
                    setBody(org)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Conflict
                }
            }
        }

        test("PUT /organizations/{organizationId} should update an organization") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }

                val org = Organization(name = "testOrg", description = "description of testOrg")
                val createdOrg = OrganizationsRepository.createOrganization(org.name, org.description)

                val client = createJsonClient()

                val updatedOrganization = Organization(name = "updated", description = "updated description of testOrg")
                val response = client.put("/api/v1/organizations/${createdOrg.id}") {
                    headers { contentType(ContentType.Application.Json) }
                    setBody(updatedOrganization)
                }

                with(response) {
                    status shouldBe HttpStatusCode.OK
                    body<Organization>() shouldBe updatedOrganization.copy(id = 1)
                }

                OrganizationsRepository.getOrganization(createdOrg.id)?.mapToApiModel()
                    .shouldBe(updatedOrganization.copy(id = createdOrg.id))
            }
        }

        test("DELETE /organizations/{organizationId} should delete an organization") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }

                val org = Organization(name = "testOrg", description = "description of testOrg")
                val createdOrg = OrganizationsRepository.createOrganization(org.name, org.description)

                val client = createJsonClient()

                val response = client.delete("/api/v1/organizations/${createdOrg.id}")

                with(response) {
                    status shouldBe HttpStatusCode.NoContent
                }

                OrganizationsRepository.listOrganizations() shouldBe emptyList()
            }
        }
    }
}
