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

package org.eclipse.apoapsis.ortserver.components.secrets

import io.ktor.client.HttpClient
import io.ktor.server.testing.ApplicationTestBuilder

import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.services.SecretService
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

/** An [AbstractIntegrationTest] pre-configured for testing the secrets routes. */
@Suppress("UnnecessaryAbstractClass")
abstract class SecretsIntegrationTest(body: SecretsIntegrationTest.() -> Unit) : AbstractIntegrationTest({}) {
    lateinit var repositoryService: RepositoryService
    lateinit var secretRepository: SecretRepository
    lateinit var secretService: SecretService

    val secretErrorPath = "error-path"

    init {
        beforeEach {
            repositoryService = RepositoryService(
                dbExtension.db,
                dbExtension.fixtures.ortRunRepository,
                dbExtension.fixtures.repositoryRepository,
                dbExtension.fixtures.analyzerJobRepository,
                dbExtension.fixtures.advisorJobRepository,
                dbExtension.fixtures.scannerJobRepository,
                dbExtension.fixtures.evaluatorJobRepository,
                dbExtension.fixtures.reporterJobRepository,
                dbExtension.fixtures.notifierJobRepository,
                mockk()
            )
            secretRepository = dbExtension.fixtures.secretRepository
            secretService = SecretService(
                dbExtension.db,
                dbExtension.fixtures.secretRepository,
                SecretStorage(SecretsProviderFactoryForTesting().createProvider(secretErrorPath))
            )
        }

        body()
    }

    fun secretsTestApplication(
        block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit
    ) = integrationTestApplication(
        routes = { secretsRoutes(repositoryService, secretService) },
        validations = { secretsValidations() },
        block = block
    )
}
