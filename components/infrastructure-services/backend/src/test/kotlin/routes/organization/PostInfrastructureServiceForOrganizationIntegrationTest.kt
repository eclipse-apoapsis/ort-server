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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages

import org.eclipse.apoapsis.ortserver.components.infrastructureservices.CreateInfrastructureService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServicesIntegrationTest
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InvalidSecretReferenceException
import org.eclipse.apoapsis.ortserver.dao.UniqueConstraintException
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.apimodel.CredentialsType
import org.eclipse.apoapsis.ortserver.shared.apimodel.ErrorResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.InfrastructureService
import org.eclipse.apoapsis.ortserver.shared.ktorutils.respondError
import org.eclipse.apoapsis.ortserver.shared.ktorutils.shouldHaveBody

class PostInfrastructureServiceForOrganizationIntegrationTest : InfrastructureServicesIntegrationTest({
    "PostInfrastructureServiceForOrganization" should {
        "create an infrastructure service" {
            infrastructureServicesTestApplication { client ->
                val createInfrastructureService = CreateInfrastructureService(
                    "testRepository",
                    "https://repo.example.org/test",
                    "test description",
                    orgUserSecret.name,
                    orgPassSecret.name,
                    credentialsTypes = setOf(
                        CredentialsType.GIT_CREDENTIALS_FILE,
                        CredentialsType.NETRC_FILE,
                        CredentialsType.NO_AUTHENTICATION
                    )
                )

                val response = client.post("/organizations/$orgId/infrastructure-services") {
                    setBody(createInfrastructureService)
                }

                val expectedService = InfrastructureService(
                    createInfrastructureService.name,
                    createInfrastructureService.url,
                    createInfrastructureService.description,
                    orgUserSecret.name,
                    orgPassSecret.name,
                    setOf(
                        CredentialsType.GIT_CREDENTIALS_FILE,
                        CredentialsType.NETRC_FILE,
                        CredentialsType.NO_AUTHENTICATION
                    )
                )

                response shouldHaveStatus HttpStatusCode.Created
                response shouldHaveBody expectedService

                val dbService = infrastructureServiceRepository.getByIdAndName(
                    OrganizationId(orgId),
                    createInfrastructureService.name
                )
                dbService.shouldNotBeNull()
                dbService.mapToApi() shouldBe expectedService
            }
        }

        "handle an invalid secret reference" {
            infrastructureServicesTestApplication { client ->
                install(StatusPages) {
                    // TODO: This should use the same config as in core.
                    exception<InvalidSecretReferenceException> { call, e ->
                        call.respondError(
                            HttpStatusCode.BadRequest,
                            message = "Secret reference could not be resolved.",
                            cause = e.message
                        )
                    }
                }

                val createInfrastructureService = CreateInfrastructureService(
                    "testRepository",
                    "https://repo.example.org/test",
                    "test description",
                    "nonExistingSecret1",
                    "nonExistingSecret2"
                )

                val response = client.post("/organizations/$orgId/infrastructure-services") {
                    setBody(createInfrastructureService)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest
                response.body<ErrorResponse>().cause shouldContain "nonExistingSecret"
            }
        }

        "respond with 'Bad Request' if the infrastructure service's name is invalid" {
            infrastructureServicesTestApplication { client ->
                install(StatusPages) {
                    // TODO: This should use the same config as in core.
                    exception<RequestValidationException> { call, e ->
                        call.respondError(
                            HttpStatusCode.BadRequest,
                            message = "Request validation has failed.",
                            cause = e.message
                        )
                    }
                }

                val createInfrastructureService = CreateInfrastructureService(
                    " testRepository 15?!",
                    "https://repo.example.org/test",
                    "test description",
                    orgUserSecret.name,
                    orgPassSecret.name
                )

                val response = client.post("/organizations/$orgId/infrastructure-services") {
                    setBody(createInfrastructureService)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for CreateInfrastructureService"

                infrastructureServiceRepository.getByIdAndName(
                    OrganizationId(orgId),
                    createInfrastructureService.name
                ).shouldBeNull()
            }
        }

        "respond with 'Conflict' if service with same name and orgId already exists" {
            infrastructureServicesTestApplication { client ->
                install(StatusPages) {
                    // TODO: This should use the same config as in core.
                    exception<UniqueConstraintException> { call, e ->
                        call.respondError(
                            HttpStatusCode.Conflict,
                            message = "The entity you tried to create already exists.",
                            cause = e.message
                        )
                    }
                }

                val createdInfrastructureService = infrastructureServiceRepository.create(
                    "testRepository",
                    "https://repo.example.org/test",
                    "test repo description",
                    orgUserSecret,
                    orgPassSecret,
                    emptySet(),
                    OrganizationId(orgId)
                )

                val createInfrastructureService = CreateInfrastructureService(
                    createdInfrastructureService.name,
                    createdInfrastructureService.url,
                    "test repo description",
                    orgUserSecret.name,
                    orgPassSecret.name,
                    credentialsTypes = emptySet()
                )

                val response = client.post("/organizations/$orgId/infrastructure-services") {
                    setBody(createInfrastructureService)
                }

                response shouldHaveStatus HttpStatusCode.Conflict
            }
        }
    }
})
