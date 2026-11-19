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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName

import kotlin.time.Duration.Companion.milliseconds

import org.eclipse.apoapsis.ortserver.components.authorization.routes.OrtServerPrincipal
import org.eclipse.apoapsis.ortserver.core.utils.ClientIpResolver

import org.koin.ktor.ext.inject

fun Application.configureRateLimit() {
    val config: ApplicationConfig by inject()
    val clientIpResolver = ClientIpResolver.create(config)

    val publicMaxRequests = config.property("ktor.rateLimit.public.maxRequests").getString().toInt()
    val publicWindowMs = config.property("ktor.rateLimit.public.windowMs").getString().toLong()
    val authorizedMaxRequests = config.property("ktor.rateLimit.authorized.maxRequests").getString().toInt()
    val authorizedWindowMs = config.property("ktor.rateLimit.authorized.windowMs").getString().toLong()

    install(RateLimit) {
        register(RateLimitName("public")) {
            rateLimiter(limit = publicMaxRequests, refillPeriod = publicWindowMs.milliseconds)
            requestKey { call ->
                "ip:${clientIpResolver.resolve(call.request)}"
            }
        }

        register(RateLimitName("authorized")) {
            rateLimiter(limit = authorizedMaxRequests, refillPeriod = authorizedWindowMs.milliseconds)
            requestKey { call ->
                // The user ID is empty if an exception occurred while creating the principal.
                val userId = call.principal<OrtServerPrincipal>()?.userId?.takeUnless { it.isEmpty() }
                if (userId != null) "user:$userId" else "ip:${clientIpResolver.resolve(call.request)}"
            }
        }
    }
}
