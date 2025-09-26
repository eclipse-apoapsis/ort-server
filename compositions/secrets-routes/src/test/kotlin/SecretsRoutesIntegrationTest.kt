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

package org.eclipse.apoapsis.ortserver.compositions.secretsroutes

import io.ktor.client.HttpClient
import io.ktor.server.testing.ApplicationTestBuilder

import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServiceService
import org.eclipse.apoapsis.ortserver.components.secrets.SecretService
import org.eclipse.apoapsis.ortserver.components.secrets.secretsValidations
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

/** An [AbstractIntegrationTest] pre-configured for testing the secrets routes. */
@Suppress("UnnecessaryAbstractClass")
abstract class SecretsRoutesIntegrationTest(
    body: SecretsRoutesIntegrationTest.() -> Unit
) : AbstractIntegrationTest({}) {
    lateinit var infrastructureServiceService: InfrastructureServiceService
    lateinit var secretRepository: SecretRepository
    lateinit var secretService: SecretService

    val secretErrorPath = "error-path"

    init {
        beforeEach {
            secretRepository = dbExtension.fixtures.secretRepository
            secretService = SecretService(
                dbExtension.db,
                dbExtension.fixtures.secretRepository,
                SecretStorage(SecretsProviderFactoryForTesting().createProvider(secretErrorPath))
            )
            infrastructureServiceService = InfrastructureServiceService(dbExtension.db, secretService)
        }

        body()
    }

    fun secretsRoutesTestApplication(
        block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit
    ) = integrationTestApplication(
        routes = { secretsCompositionRoutes(infrastructureServiceService, secretService) },
        validations = { secretsValidations() },
        block = block
    )
}
