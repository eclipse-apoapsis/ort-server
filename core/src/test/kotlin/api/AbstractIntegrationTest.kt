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

package org.eclipse.apoapsis.ortserver.core.api

import io.kotest.core.spec.style.WordSpec
import io.ktor.client.HttpClient

import io.ktor.server.testing.ApplicationTestBuilder

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.core.createJsonClient
import org.eclipse.apoapsis.ortserver.core.testutils.authNoDbConfig
import org.eclipse.apoapsis.ortserver.core.testutils.ortServerTestApplication
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting

@Suppress("UnnecessaryAbstractClass")
abstract class AbstractIntegrationTest(body: AbstractIntegrationTest.() -> Unit) : WordSpec() {
    val dbExtension = extension(DatabaseTestExtension())

    val secretValue = "secret-value"
    val secretErrorPath = "error-path"

    protected val secretsConfig = mapOf(
        "${SecretStorage.CONFIG_PREFIX}.${SecretStorage.NAME_PROPERTY}" to SecretsProviderFactoryForTesting.NAME,
        "${SecretStorage.CONFIG_PREFIX}.${SecretsProviderFactoryForTesting.ERROR_PATH_PROPERTY}" to secretErrorPath
    )

    protected open val additionalConfig = secretsConfig

    private val json = Json { ignoreUnknownKeys = true }

    val ApplicationTestBuilder.apiClient: HttpClient
        get() = createJsonClient()

    init {
        body()
    }

    fun integrationTestApplication(block: suspend ApplicationTestBuilder.() -> Unit) =
        ortServerTestApplication(dbExtension.db, authNoDbConfig, additionalConfig, block)
}
