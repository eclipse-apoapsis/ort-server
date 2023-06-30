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

import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_REALM
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_SUBJECT_CLIENT
import org.ossreviewtoolkit.server.clients.keycloak.test.testRealm
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
