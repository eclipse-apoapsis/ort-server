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
import org.eclipse.apoapsis.ortserver.core.testutils.configureTestAuthentication
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension

import org.koin.ktor.ext.get

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

/**
 * A test application configuration that removes or replaces components with a dummy version that are required for
 * most integration tests.
 * * Database: This application does not connect to a database. Instead, [DatabaseTestExtension] should be used to
 *             connect to a test database backed by [Testcontainers](https://www.testcontainers.org/).
 * * Authentication: This application does not use KeyCloak for authentication. Instead, it uses an
 *                   authentication which is always valid.
 */
fun Application.testModule() {
    configureKoin(setupDatabase = false)
    configureTestAuthentication()
    configureStatusPages()
    configureRouting()
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureValidation()
}

/**
 * A test application configuration that requires Keycloak to be run in the integration test.
 */
fun Application.testAuthModule() {
    configureKoin(setupDatabase = false)
    configureAuthentication(get(), get())
    configureStatusPages()
    configureRouting()
    configureSerialization()
    configureMonitoring()
    configureHTTP()
    configureValidation()
}
