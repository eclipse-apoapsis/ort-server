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

package org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.product

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServicesIntegrationTest
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.PatchInfrastructureService
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.apimodel.CredentialsType
import org.eclipse.apoapsis.ortserver.shared.apimodel.InfrastructureService
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.ktorutils.shouldHaveBody

class PatchProductInfrastructureServiceIntegrationTest : InfrastructureServicesIntegrationTest({
    "PatchProductInfrastructureService" should {
        "update an infrastructure service" {
            infrastructureServicesTestApplication { client ->
                val service = infrastructureServiceService.createForId(
                    ProductId(prodId),
                    "updateService",
                    "http://repo1.example.org/test",
                    "test description",
                    prodUserSecret,
                    prodPassSecret,
                    emptySet()
                )

                val newUrl = "https://repo2.example.org/test2"
                val updateService = PatchInfrastructureService(
                    description = null.asPresent(),
                    url = newUrl.asPresent(),
                    credentialsTypes = EnumSet.of(
                        CredentialsType.NETRC_FILE,
                        CredentialsType.GIT_CREDENTIALS_FILE
                    ).asPresent()
                )

                val response = client.patch("/products/$prodId/infrastructure-services/${service.name}") {
                    setBody(updateService)
                }

                val updatedService = InfrastructureService(
                    service.name,
                    newUrl,
                    null,
                    prodUserSecret,
                    prodPassSecret,
                    EnumSet.of(CredentialsType.NETRC_FILE, CredentialsType.GIT_CREDENTIALS_FILE)
                )

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody updatedService

                val dbService = infrastructureServiceService.getForId(ProductId(prodId), service.name)
                dbService.shouldNotBeNull()
                dbService.mapToApi() shouldBe updatedService
            }
        }
    }
})
