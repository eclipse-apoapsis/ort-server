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

package org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.organization

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServicesIntegrationTest
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.shared.apimodel.CredentialsType as ApiCredentialsType
import org.eclipse.apoapsis.ortserver.shared.apimodel.InfrastructureService
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse

class GetOrganizationInfrastructureServicesIntegrationTest : InfrastructureServicesIntegrationTest({
    "GetOrganizationInfrastructureServices" should {
        "list existing infrastructure services" {
            infrastructureServicesTestApplication { client ->
                val services = (1..8).map { index ->
                    infrastructureServiceService.createForId(
                        OrganizationId(orgId),
                        "infrastructureService$index",
                        "https://repo.example.org/test$index",
                        "description$index",
                        orgUserSecret,
                        orgPassSecret,
                        if (index % 2 == 0) {
                            EnumSet.of(CredentialsType.NETRC_FILE, CredentialsType.GIT_CREDENTIALS_FILE)
                        } else {
                            emptySet()
                        }
                    )
                }

                val apiServices = services.map { service ->
                    InfrastructureService(
                        service.name,
                        service.url,
                        service.description,
                        service.usernameSecret.name,
                        service.passwordSecret.name,
                        if (service.credentialsTypes.isEmpty()) {
                            emptySet()
                        } else {
                            EnumSet.of(ApiCredentialsType.NETRC_FILE, ApiCredentialsType.GIT_CREDENTIALS_FILE)
                        }
                    )
                }

                val response = client.get("/organizations/$orgId/infrastructure-services")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<PagedResponse<InfrastructureService>>().data shouldContainExactlyInAnyOrder
                        apiServices
            }
        }

        "support query parameters" {
            infrastructureServicesTestApplication { client ->
                (1..8).shuffled().forEach { index ->
                    infrastructureServiceService.createForId(
                        OrganizationId(orgId),
                        "infrastructureService$index",
                        "https://repo.example.org/test$index",
                        "description$index",
                        orgUserSecret,
                        orgPassSecret,
                        EnumSet.of(CredentialsType.NETRC_FILE)

                    )
                }

                val apiServices = (1..4).map { index ->
                    InfrastructureService(
                        "infrastructureService$index",
                        "https://repo.example.org/test$index",
                        "description$index",
                        orgUserSecret,
                        orgPassSecret,
                        EnumSet.of(ApiCredentialsType.NETRC_FILE)
                    )
                }

                val response = client.get("/organizations/$orgId/infrastructure-services?sort=name&limit=4")

                response shouldHaveStatus HttpStatusCode.OK
                response.body<PagedResponse<InfrastructureService>>().data shouldContainExactlyInAnyOrder
                        apiServices
            }
        }
    }
})
