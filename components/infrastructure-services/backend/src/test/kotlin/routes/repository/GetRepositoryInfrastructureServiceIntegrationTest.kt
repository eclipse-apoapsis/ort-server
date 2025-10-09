/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServicesIntegrationTest
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.ktorutils.shouldHaveBody

class GetRepositoryInfrastructureServiceIntegrationTest : InfrastructureServicesIntegrationTest({
    "GetRepositoryInfrastructureService" should {
        "return an infrastructure service" {
            infrastructureServicesTestApplication { client ->
                val service = infrastructureServiceService.createForId(
                    RepositoryId(repoId),
                    "testRepository",
                    "http://repo.example.org/test",
                    "test repo description",
                    repoUserSecret,
                    repoPassSecret,
                    emptySet()
                )

                val response = client.get("/repositories/$repoId/infrastructure-services/${service.name}")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody service.mapToApi()
            }
        }

        "respond with 'NotFound' if no infrastructure service exists" {
            infrastructureServicesTestApplication { client ->
                val response = client.get("/repositories/$repoId/infrastructure-services/not-existing-service")

                response shouldHaveStatus HttpStatusCode.NotFound
            }
        }
    }
})
