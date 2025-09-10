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

package org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.repository

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServicesIntegrationTest
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.UpdateInfrastructureService
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.apimodel.CredentialsType
import org.eclipse.apoapsis.ortserver.shared.apimodel.InfrastructureService
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.ktorutils.shouldHaveBody

class PatchInfrastructureServiceForRepositoryIdAndNameIntegrationTest : InfrastructureServicesIntegrationTest({
    "PatchInfrastructureServiceForRepositoryIdAndName" should {
        "update an infrastructure service" {
            infrastructureServicesTestApplication { client ->
                val service = infrastructureServiceRepository.create(
                    "updateService",
                    "http://repo1.example.org/test",
                    "test description",
                    repoUserSecret,
                    repoPassSecret,
                    emptySet(),
                    RepositoryId(repoId)
                )

                val newUrl = "https://repo2.example.org/test2"
                val updateService = UpdateInfrastructureService(
                    description = null.asPresent(),
                    url = newUrl.asPresent(),
                    credentialsTypes = EnumSet.of(
                        CredentialsType.NETRC_FILE,
                        CredentialsType.GIT_CREDENTIALS_FILE
                    ).asPresent()
                )
                val response = client.patch("/repositories/$repoId/infrastructure-services/${service.name}") {
                    setBody(updateService)
                }

                val updatedService = InfrastructureService(
                    service.name,
                    newUrl,
                    null,
                    repoUserSecret.name,
                    repoPassSecret.name,
                    EnumSet.of(CredentialsType.NETRC_FILE, CredentialsType.GIT_CREDENTIALS_FILE)
                )

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody updatedService

                val dbService =
                    infrastructureServiceRepository.getByIdAndName(RepositoryId(repoId), service.name)
                dbService.shouldNotBeNull()
                dbService.mapToApi() shouldBe updatedService
            }
        }
    }
})
