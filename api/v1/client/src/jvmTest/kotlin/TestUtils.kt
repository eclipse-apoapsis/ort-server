/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Create JSON respond with the given [obj] and [statusCode].
 */
internal inline fun <reified T : Any> MockRequestHandleScope.jsonRespond(
    obj: T,
    statusCode: HttpStatusCode = HttpStatusCode.OK
): HttpResponseData = respond(
    content = Json.encodeToString(obj),
    status = statusCode,
    headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
)
