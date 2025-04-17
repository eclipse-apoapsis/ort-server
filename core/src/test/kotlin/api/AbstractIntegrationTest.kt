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

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.WordSpec

import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.clients.keycloak.DefaultKeycloakClient.Companion.configureAuthentication
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.KeycloakTestExtension
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.TEST_SUBJECT_CLIENT
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.addUserRole
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.createJwtConfigMapForTestRealm
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.createKeycloakClientConfigurationForTestRealm
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.createKeycloakClientForTestRealm
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.createKeycloakConfigMapForTestRealm
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.setUpClientScope
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.setUpUser
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.setUpUserRoles
import org.eclipse.apoapsis.ortserver.components.authorization.roles.Superuser
import org.eclipse.apoapsis.ortserver.core.SUPERUSER
import org.eclipse.apoapsis.ortserver.core.SUPERUSER_PASSWORD
import org.eclipse.apoapsis.ortserver.core.TEST_USER
import org.eclipse.apoapsis.ortserver.core.TEST_USER_PASSWORD
import org.eclipse.apoapsis.ortserver.core.createJsonClient
import org.eclipse.apoapsis.ortserver.core.testutils.TestConfig
import org.eclipse.apoapsis.ortserver.core.testutils.ortServerTestApplication
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting

@Suppress("UnnecessaryAbstractClass")
abstract class AbstractIntegrationTest(body: AbstractIntegrationTest.() -> Unit) : WordSpec() {
    val dbExtension = extension(DatabaseTestExtension())

    val keycloak = install(KeycloakTestExtension(createRealmPerTest = true)) {
        setUpUser(SUPERUSER, SUPERUSER_PASSWORD)
        setUpUserRoles(SUPERUSER.username.value, listOf(Superuser.ROLE_NAME))
        setUpUser(TEST_USER, TEST_USER_PASSWORD)
        setUpClientScope(TEST_SUBJECT_CLIENT)
    }

    val keycloakClient = keycloak.createKeycloakClientForTestRealm()

    private val keycloakConfig = keycloak.createKeycloakConfigMapForTestRealm()
    private val jwtConfig = keycloak.createJwtConfigMapForTestRealm()

    val secretValue = "secret-value"
    val secretErrorPath = "error-path"

    private val secretsConfig = mapOf(
        "${SecretStorage.CONFIG_PREFIX}.${SecretStorage.NAME_PROPERTY}" to SecretsProviderFactoryForTesting.NAME,
        "${SecretStorage.CONFIG_PREFIX}.${SecretsProviderFactoryForTesting.ERROR_PATH_PROPERTY}" to secretErrorPath
    )

    private val additionalConfig = keycloakConfig + jwtConfig + secretsConfig

    private val superuserClientConfig = keycloak.createKeycloakClientConfigurationForTestRealm(
        user = SUPERUSER.username.value,
        secret = SUPERUSER_PASSWORD
    )

    private val testUserClientConfig = keycloak.createKeycloakClientConfigurationForTestRealm(
        user = TEST_USER.username.value,
        secret = TEST_USER_PASSWORD
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * A convenience property to create an [HttpClient] which authenticates as [SUPERUSER]. Note that each call to the
     * property creates a new client, so if the client is used multiple times in a test, consider storing it in a
     * variable.
     */
    val ApplicationTestBuilder.superuserClient: HttpClient
        get() = createJsonClient().configureAuthentication(superuserClientConfig, json)

    /**
     * A convenience property to create an [HttpClient] which authenticates as [TEST_USER]. Note that each call to the
     * property creates a new client, so if the client is used multiple times in a test, consider storing it in a
     * variable.
     */
    val ApplicationTestBuilder.testUserClient: HttpClient
        get() = createJsonClient().configureAuthentication(testUserClientConfig, json)

    val ApplicationTestBuilder.unauthenticatedClient: HttpClient
        get() = createJsonClient()

    init {
        body()
    }

    fun integrationTestApplication(block: suspend ApplicationTestBuilder.() -> Unit) =
        ortServerTestApplication(dbExtension.db, TestConfig.TestAuth, additionalConfig, block)

    fun requestShouldRequireRole(
        role: String,
        successStatus: HttpStatusCode = HttpStatusCode.OK,
        request: suspend HttpClient.() -> HttpResponse
    ) {
        integrationTestApplication {
            val client = testUserClient

            client.request() shouldHaveStatus HttpStatusCode.Forbidden
            keycloak.keycloakAdminClient.addUserRole(TEST_USER.username.value, role)
            client.request() shouldHaveStatus successStatus
        }
    }

    fun requestShouldRequireAuthentication(
        successStatus: HttpStatusCode = HttpStatusCode.OK,
        request: suspend HttpClient.() -> HttpResponse
    ) {
        integrationTestApplication {
            unauthenticatedClient.request() shouldHaveStatus HttpStatusCode.Unauthorized
            testUserClient.request() shouldHaveStatus successStatus
        }
    }
}
