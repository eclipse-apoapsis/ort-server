/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core

import dasniko.testcontainers.keycloak.KeycloakContainer

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.util.KtorDsl
import io.ktor.util.appendIfNameAbsent

import kotlinx.serialization.json.Json

import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.ClientScopeRepresentation
import org.keycloak.representations.idm.ProtocolMapperRepresentation
import org.keycloak.representations.idm.RoleRepresentation

import org.ossreviewtoolkit.server.clients.keycloak.User
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_CLIENT
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_REALM
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_SUBJECT_CLIENT
import org.ossreviewtoolkit.server.clients.keycloak.test.testRealm
import org.ossreviewtoolkit.server.clients.keycloak.test.toUserRepresentation
import org.ossreviewtoolkit.server.core.testutils.ortServerTestApplication

/**
 * Create a client with [JSON ContentNegotiation][json] installed and default content type set to `application/json`.
 * Can be further configured with [block].
 */
@KtorDsl
fun ApplicationTestBuilder.createJsonClient(
    json: Json = Json,
    block: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit = {}
): HttpClient = createClient {
    install(ContentNegotiation) {
        json(json)
    }

    defaultRequest {
        headers.appendIfNameAbsent(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }

    block()
}

/**
 * Create a [user] with the provided [password].
 */
fun Keycloak.setUpUser(user: User, password: String) {
    realm(TEST_REALM).apply {
        users().create(user.toUserRepresentation(password = password))
    }
}

/**
 * Create an audience mapper that adds the provided [audience] to the access token.
 */
private fun audienceMapper(audience: String) = ProtocolMapperRepresentation().apply {
    name = "audience-mapper"
    protocol = "openid-connect"
    protocolMapper = "oidc-audience-mapper"
    config = mapOf(
        "included.client.audience" to audience,
        "id.token.claim" to "false",
        "access.token.claim" to "true",
        "userinfo.token.claim" to "false"
    )
}

/**
 * Create a client scope that maps the provided [audience] to the audience in the JWT token and add the created scope to
 * the default scopes of the [TEST_CLIENT].
 */
fun Keycloak.setUpClientScope(audience: String) {
    val subjectClientScope = "$TEST_SUBJECT_CLIENT-scope"

    realm(TEST_REALM).apply {
        clientScopes().create(
            ClientScopeRepresentation().apply {
                name = subjectClientScope
                protocol = "openid-connect"
                protocolMappers = listOf(audienceMapper(audience))
            }
        )

        val testClient = clients().get(clients().findByClientId(TEST_CLIENT).first().id)
        val clientScope = clientScopes().findAll().single { it.name == subjectClientScope }
        testClient.addDefaultClientScope(clientScope.id)
    }
}

/**
 * Create the provided [roles] in the [TEST_SUBJECT_CLIENT] and assign them to the provided [username].
 */
fun Keycloak.setUpUserRoles(username: String, roles: List<String>) {
    realm(TEST_REALM).apply {
        val client = clients().findByClientId(TEST_SUBJECT_CLIENT).single()

        val roleRepresentations = roles.map { role ->
            clients().get(client.id).roles().run {
                create(RoleRepresentation().apply { name = role })
                get(role).toRepresentation()
            }
        }

        val user = users().search(username).single()
        users().get(user.id).roles().clientLevel(client.id).add(roleRepresentations)
    }
}

/**
 * Add the provided [role] in the [TEST_SUBJECT_CLIENT] to the provided [username].
 */
fun Keycloak.addUserRole(username: String, role: String) {
    realm(TEST_REALM).apply {
        val client = clients().findByClientId(TEST_SUBJECT_CLIENT).single()
        val user = users().search(username).single()

        val roleRepresentation = clients().get(client.id).roles().get(role).toRepresentation()
        users().get(user.id).roles().clientLevel(client.id).add(listOf(roleRepresentation))
    }
}

/**
 * Create a map containing JWT configuration properties for the [testRealm] and this [KeycloakContainer]. The map can be
 * used to configure the [ortServerTestApplication].
 */
fun KeycloakContainer.createJwtConfigMapForTestRealm() =
    mapOf(
        "jwt.jwksUri" to "${authServerUrl}realms/$TEST_REALM/protocol/openid-connect/certs",
        "jwt.issuer" to "${authServerUrl}realms/$TEST_REALM",
        "jwt.realm" to TEST_REALM,
        "jwt.audience" to TEST_SUBJECT_CLIENT
    )
