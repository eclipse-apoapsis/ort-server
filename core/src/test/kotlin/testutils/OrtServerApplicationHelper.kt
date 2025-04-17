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

import io.ktor.server.application.ApplicationStarted
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.KtorDsl

import org.jetbrains.exposed.sql.Database

import org.koin.core.context.stopKoin
import org.koin.ktor.plugin.KOIN_ATTRIBUTE_KEY

/**
 * Test helper for integration tests, which configures a test application using the given [applicationConfig][config]
 * merged with [additionalConfigs]. The [additionalConfigs] take precedence over the [config].
 */
@KtorDsl
fun ortServerTestApplication(
    db: Database? = null,
    config: ApplicationConfig = defaultConfig,
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

    val mergedConfig = config.mergeWith(additionalConfig)

    environment {
        this.config = mergedConfig
    }

    serverConfig {
        // Turn off development mode for tests because auto-reloading causes several issues, mainly with code checking
        // if an object is an instance of a certain class, because the same class might have been loaded by different
        // class loaders.
        developmentMode = false
    }

    if (db != null) {
        application {
            monitor.subscribe(ApplicationStarted) {
                attributes[KOIN_ATTRIBUTE_KEY].koin.declare(db)
            }
        }
    }

    block()
}

/** The default application configuration. */
val defaultConfig = ApplicationConfig("application.conf")

/** An application configuration with token authentication, without a database. */
val authNoDbConfig = ApplicationConfig("application-test-auth.conf")

/** An application configuration without a database and a dummy authentication. */
val noDbConfig = ApplicationConfig("application-test.conf")
