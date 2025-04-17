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

package org.eclipse.apoapsis.ortserver.core.testutils

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.KtorDsl

import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.core.testAuthModule
import org.eclipse.apoapsis.ortserver.core.testModule

import org.jetbrains.exposed.sql.Database

import org.koin.core.context.stopKoin

/**
 * Test helper for integration tests, which configures a test application using the given [applicationConfig][config]
 * merged with [additionalConfigs]. The [additionalConfigs] take precedence over the [config].
 */
@KtorDsl
fun ortServerTestApplication(
    db: Database? = null,
    config: TestConfig = TestConfig.Default,
    additionalConfigs: Map<String, Any> = mapOf(),
    block: suspend ApplicationTestBuilder.() -> Unit
) = testApplication {
    // If a test fails, Koin keeps running which causes subsequent tests to fail with "A Koin Application has already
    // been started". Prevent this by making sure that any running Koin is stopped when starting a new test application.
    stopKoin()

    val additionalConfig = MapApplicationConfig()
    additionalConfigs.forEach { (path, value) ->
        @Suppress("UNCHECKED_CAST")
        when (value) {
            is String -> additionalConfig.put(path, value)
            is Iterable<*> -> additionalConfig.put(path, value as Iterable<String>)
            else -> IllegalArgumentException(
                "Value '$value' cannot be added as an application configuration. The configuration type has to be " +
                        "either 'String' or 'Iterable<String>'."
            )
        }
    }

    val mergedConfig = config.config.mergeWith(additionalConfig)

    environment {
        this.config = mergedConfig
    }

    serverConfig {
        // Turn off development mode for tests because auto-reloading causes several issues, mainly with code checking
        // if an object is an instance of a certain class, because the same class might have been loaded by different
        // class loaders.
        developmentMode = false
    }

    application {
        // If no database is provided, use a mock database to prevent Koin from trying to connect to a real database.
        config.run { setupModules(db ?: mockk()) }
    }

    block()
}

/**
 * Constants for different integration test configs.
 */
sealed interface TestConfig {
    val config: ApplicationConfig
    fun Application.setupModules(db: Database)

    /**
     * The default application config that is used in production.
     */
    object Default : TestConfig {
        override val config = ApplicationConfig("application.conf")
        override fun Application.setupModules(db: Database) {
            // No-op, because the default application.conf set ktor.application.modules.
        }
    }

    /**
     * An application config that uses a test database and expects that the Keycloak config is provided separately.
     */
    object TestAuth : TestConfig {
        override val config = ApplicationConfig("application-test-auth.conf")
        override fun Application.setupModules(db: Database) {
            testAuthModule(db)
        }
    }

    /**
     * An application config that uses a test database and provides an empty Keycloak config, to be used for tests that
     * do not require authentication.
     */
    object Test : TestConfig {
        override val config = ApplicationConfig("application-test.conf")
        override fun Application.setupModules(db: Database) {
            testModule(db)
        }
    }
}
