/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.util.appendIfNameAbsent
import io.ktor.utils.io.KtorDsl

import kotlinx.serialization.json.Json

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
