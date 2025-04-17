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

import io.ktor.server.application.Application

import org.eclipse.apoapsis.ortserver.components.authorization.configureAuthentication
import org.eclipse.apoapsis.ortserver.core.plugins.*

import org.koin.ktor.ext.get

import org.slf4j.MDC

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    MDC.put("component", "core")

    configureKoin()
    configureAuthentication(get(), get())
    configureLifecycle()
    configureStatusPages()
    configureRouting()
    configureSerialization()
    configureMonitoring()
    configureMetrics()
    configureHTTP()
    configureOpenApi()
    configureValidation()
}
