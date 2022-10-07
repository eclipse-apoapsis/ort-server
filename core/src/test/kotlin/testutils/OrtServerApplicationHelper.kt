/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.testutils

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.merge
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.KtorDsl

import java.lang.IllegalArgumentException

/**
 * Test helper for integration tests, which configures a test application using the given [applicationConfig][config]
 * merged with [additionalConfigs]. The [additionalConfigs] take precedence over the [config].
 */
@KtorDsl
fun ortServerTestApplication(
    config: ApplicationConfig = defaultConfig,
    additionalConfigs: Map<String, Any> = mapOf(),
    block: suspend ApplicationTestBuilder.() -> Unit
) = testApplication {
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

    val mergedConfig = listOf(additionalConfig, config).merge()

    environment { this.config = mergedConfig }

    block()
}

/** The default application configuration. */
val defaultConfig = ApplicationConfig("application.conf")

/** An application configuration without a database. */
val noDbConfig = ApplicationConfig("application-nodb.conf")
