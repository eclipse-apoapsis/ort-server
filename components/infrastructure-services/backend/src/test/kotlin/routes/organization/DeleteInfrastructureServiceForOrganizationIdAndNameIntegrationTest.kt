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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should

import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServicesIntegrationTest
import org.eclipse.apoapsis.ortserver.model.OrganizationId

class DeleteInfrastructureServiceForOrganizationIdAndNameIntegrationTest : InfrastructureServicesIntegrationTest({
    "DeleteInfrastructureServiceForOrganizationIdAndName" should {
        "delete an infrastructure service" {
            infrastructureServicesTestApplication { client ->
                val service = infrastructureServiceRepository.create(
                    "deleteService",
                    "http://repo1.example.org/obsolete",
                    "good bye, cruel world",
                    orgUserSecret,
                    orgPassSecret,
                    emptySet(),
                    OrganizationId(orgId)
                )

                val response = client.delete("/organizations/$orgId/infrastructure-services/${service.name}")

                response shouldHaveStatus HttpStatusCode.NoContent
                infrastructureServiceRepository.listForId(OrganizationId(orgId)).data should beEmpty()
            }
        }
    }
})
