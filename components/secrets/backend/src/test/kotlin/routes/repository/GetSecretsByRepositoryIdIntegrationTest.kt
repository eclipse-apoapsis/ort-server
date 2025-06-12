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

package org.eclipse.apoapsis.ortserver.components.secrets.routes.repository

import io.kotest.assertions.ktor.client.shouldHaveStatus

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.secrets.mapToApi
import org.eclipse.apoapsis.ortserver.components.secrets.routes.createRepositorySecret
import org.eclipse.apoapsis.ortserver.components.secrets.secretsRoutes
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.services.SecretService
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest
import org.eclipse.apoapsis.ortserver.shared.ktorutils.shouldHaveBody

class GetSecretsByRepositoryIdIntegrationTest : AbstractIntegrationTest({
    var repoId = 0L
    lateinit var secretRepository: SecretRepository
    lateinit var secretService: SecretService

    beforeEach {
        repoId = dbExtension.fixtures.repository.id
        secretRepository = dbExtension.fixtures.secretRepository
        secretService = SecretService(
            dbExtension.db,
            dbExtension.fixtures.secretRepository,
            dbExtension.fixtures.infrastructureServiceRepository,
            SecretStorage(SecretsProviderFactoryForTesting().createProvider())
        )
    }

    "GetSecretsByRepositoryId" should {
        "return all secrets for this repository" {
            integrationTestApplication(routes = { secretsRoutes(secretService) }) { client ->
                val secret1 = secretRepository.createRepositorySecret(repoId, "path1", "name1", "description1")
                val secret2 = secretRepository.createRepositorySecret(repoId, "path2", "name2", "description2")

                val response = client.get("/repositories/$repoId/secrets")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(secret1.mapToApi(), secret2.mapToApi()),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "support query parameters" {
            integrationTestApplication(routes = { secretsRoutes(secretService) }) { client ->
                secretRepository.createRepositorySecret(repoId, "path1", "name1", "description1")
                val secret = secretRepository.createRepositorySecret(repoId, "path2", "name2", "description2")

                val response = client.get("/repositories/$repoId/secrets?sort=-name&limit=1")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(secret.mapToApi()),
                    PagingData(
                        limit = 1,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.DESCENDING))
                    )
                )
            }
        }
    }
})
