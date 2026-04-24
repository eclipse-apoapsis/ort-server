/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.pluginmanager

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext

/**
 * Execute the given [onSuccess] block if the result is successful, otherwise respond with the appropriate HTTP status
 * code and error message based on the type of [TemplateError].
 */
context(context: RoutingContext)
internal suspend fun <V> Result<V, TemplateError>.handleTemplateResult(onSuccess: suspend (V) -> Unit) {
    onOk { onSuccess(it) }
    onErr {
        when (it) {
            is TemplateError.InvalidPlugin -> context.call.respond(HttpStatusCode.BadRequest, it.message)
            is TemplateError.InvalidState -> context.call.respond(HttpStatusCode.BadRequest, it.message)
            is TemplateError.NotFound -> context.call.respond(HttpStatusCode.NotFound, it.message)
        }
    }
}
