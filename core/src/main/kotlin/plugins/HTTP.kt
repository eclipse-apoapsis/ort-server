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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.CompressionConfig
import io.ktor.server.plugins.compression.condition
import io.ktor.server.plugins.compression.excludeContentType
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders

import org.eclipse.apoapsis.ortserver.utils.system.CustomHttpHeaders

import org.koin.ktor.ext.inject

/** The Fetch Metadata request header which browsers set to tell how the origin of a request relates to the target. */
private const val SEC_FETCH_SITE = "Sec-Fetch-Site"

fun Application.configureHTTP() {
    val config: ApplicationConfig by inject()
    val allowedHosts = config.property("ktor.cors.allowedHosts").getString().split(',').map(String::trim)

    install(Compression) {
        // Enable response compression without changing how request bodies are handled.
        mode = CompressionConfig.Mode.CompressResponse

        // Do not compress responses that already fit into a single TCP packet. As a global condition replaces Ktor's
        // default conditions, the default content type exclusions have to be repeated here.
        minimumSize(1024)
        excludeContentType(
            ContentType.Video.Any,
            ContentType.Image.JPEG,
            ContentType.Image.PNG,
            ContentType.Audio.Any,
            ContentType.MultiPart.Any,
            ContentType.Text.EventStream
        )

        // Mitigate the BREACH attack by not compressing responses to cross-site requests that a malicious page could
        // trigger in the browser of a victim. Requests from the UI are cross-site as well, but as CORS requests they
        // carry an Origin header that a page cannot forge. Cross-site requests from an Origin that is not allowed are
        // rejected by CORS before they reach this condition. If all hosts are allowed via "*", responses to any Origin
        // are compressed, which exposes nothing beyond what CORS already lets every Origin read. Browsers that do not
        // send the Sec-Fetch-Site header are not covered; this is acceptable as long as authentication relies on a
        // bearer token that a malicious page cannot make the browser attach.
        condition { isCompressionSafe(allowedHosts) }
    }

    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }
    install(CORS) {
        allowedHosts.forEach(::allowHost)
        allowCredentials = true
        allowHeader(CustomHttpHeaders.CLIENT_TYPE)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Put)
    }
}

/**
 * Return whether the response to this call can be compressed without exposing it to the BREACH attack. This is the case
 * if the request did not come from a browser on behalf of another site, i.e. if the [SEC_FETCH_SITE] header is absent,
 * or if the request was sent by the UI from one of the [allowedHosts].
 */
private fun ApplicationCall.isCompressionSafe(allowedHosts: List<String>): Boolean =
    when (request.headers[SEC_FETCH_SITE]) {
        null, "same-origin", "none" -> true

        else -> request.headers[HttpHeaders.Origin]?.substringAfter("://")?.let { originHost ->
            "*" in allowedHosts || originHost in allowedHosts
        } == true
    }
