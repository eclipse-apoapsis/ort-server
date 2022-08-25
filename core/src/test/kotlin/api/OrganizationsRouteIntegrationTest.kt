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

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication

import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.repositories.OrganizationsRepository
import org.ossreviewtoolkit.server.shared.models.api.Organization
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

class OrganizationsRouteIntegrationTest : DatabaseTest() {
    init {
        test("GET /organizations should return all existing organizations") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }
                dataSource.connect()

                val org1 = Organization(name = "testOrg1", description = "description of testOrg")
                val org2 = Organization(name = "testOrg2", description = "description of testOrg")

                val createdOrganization1 = OrganizationsRepository.createOrganization(org1.name, org1.description)
                val createdOrganization2 = OrganizationsRepository.createOrganization(org2.name, org2.description)
                createdOrganization1.shouldNotBeNull()
                createdOrganization2.shouldNotBeNull()

                val client = createClient {
                    install(ContentNegotiation) { json() }
                }

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

        test("POST /organizations should create an organization in the database") {
            testApplication {
                environment { config = ApplicationConfig("application-nodb.conf") }
                dataSource.connect()

                val client = createClient {
                    install(ContentNegotiation) { json() }
                }

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
                dataSource.connect()

                val org = Organization(name = "testOrg", description = "description of testOrg")
                OrganizationsRepository.createOrganization(org.name, org.description)

                val client = createClient {
                    install(ContentNegotiation) { json() }
                }

                val response = client.post("/api/v1/organizations") {
                    headers { contentType(ContentType.Application.Json) }
                    setBody(org)
                }

                with(response) {
                    status shouldBe HttpStatusCode.Conflict
                }
            }
        }
    }
}
