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

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.request.path

import java.util.UUID

import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            call.request.path().run {
                startsWith("/") && !contains("/liveness")
            }
        }

        mdc("component") { "core" }
        mdc("traceId") { UUID.randomUUID().toString() }
        mdc("organizationId") { it.parameters["organizationId"] }
        mdc("productId") { it.parameters["productId"] }
        mdc("repositoryId") { it.parameters["repositoryId"] }
    }
}
