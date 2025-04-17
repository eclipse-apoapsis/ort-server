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

package org.eclipse.apoapsis.ortserver.core.utils

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.file.aFile
import io.kotest.matchers.shouldBe

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

import java.io.File

import org.eclipse.apoapsis.ortserver.core.createJsonClient
import org.eclipse.apoapsis.ortserver.core.plugins.configureOpenApi
import org.eclipse.apoapsis.ortserver.core.testutils.TestConfig
import org.eclipse.apoapsis.ortserver.core.testutils.ortServerTestApplication
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension

/**
 * This fake test starts the [ortServerTestApplication] to get the generated OpenAPI specification and writes it to
 * `build/openapi/openapi.json`. This is used by the `generateOpenApiSpec` Gradle task to generate the OpenAPI
 * specification in the file system to make it accessible for generating the client for the ui module.
 *
 * This test is not executed during normal test runs and only enabled if the `generateOpenApiSpec` system property is
 * set to `true`.
 */
class GenerateOpenApiSpec : StringSpec({
    val dbExtension = extension(DatabaseTestExtension())

    "generate the OpenAPI specification".config(enabled = System.getProperty("generateOpenApiSpec").toBoolean()) {
        ortServerTestApplication(
            db = dbExtension.db,
            config = TestConfig.Test,
            additionalConfigs = mapOf("jwt.issuer" to "http://localhost:8081/realms/master")
        ) {
            application { configureOpenApi() }

            val response = createJsonClient().get("/swagger-ui/api.json")

            response shouldHaveStatus 200

            val outputDir = File("../ui/build")
            outputDir.mkdirs()

            val outputFile = outputDir.resolve("openapi.json")
            outputFile.writeText(response.bodyAsText())

            outputFile shouldBe aFile()
        }
    }
})
