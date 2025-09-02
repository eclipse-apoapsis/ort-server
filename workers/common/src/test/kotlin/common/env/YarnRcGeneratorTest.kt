/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.common.common.env

import io.kotest.core.spec.style.WordSpec
import io.kotest.extensions.system.OverrideMode
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder
import org.eclipse.apoapsis.ortserver.workers.common.env.YarnRcGenerator
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.YarnAuthMode
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.YarnDefinition

import org.ossreviewtoolkit.utils.test.withEnvironment

class YarnRcGeneratorTest : WordSpec({
    "environmentDefinitionType" should {
        "return the correct definition class" {
            val definitionType = YarnRcGenerator().environmentDefinitionType

            definitionType shouldBe YarnDefinition::class.java
        }
    }

    "generate" should {
        "generate the file at the correct location" {
            val definition = YarnDefinition(
                MockConfigFileBuilder.createInfrastructureService(REGISTRY_URI),
                null
            )

            val mockBuilder = MockConfigFileBuilder()

            YarnRcGenerator().generate(mockBuilder.builder, listOf(definition))

            mockBuilder.homeFileName shouldBe ".yarnrc.yml"
        }

        "generate a block for a registry with username and password authentication" {
            val usernameSecret = MockConfigFileBuilder.createSecret("registryUser")
            val passwordSecret = MockConfigFileBuilder.createSecret("registryPass")
            val definition = YarnDefinition(
                MockConfigFileBuilder.createInfrastructureService(REGISTRY_URI, usernameSecret, passwordSecret),
                credentialsTypes = null,
                alwaysAuth = true,
                authMode = YarnAuthMode.AUTH_IDENT
            )

            val mockBuilder = MockConfigFileBuilder()

            YarnRcGenerator().generate(mockBuilder.builder, listOf(definition))

            val expectedLines = """
                npmRegistries:
                  "$REGISTRY_URI":
                    npmAlwaysAuth: true
                    npmAuthIdent: "${MockConfigFileBuilder.testSecretRef(usernameSecret)}:${MockConfigFileBuilder.testSecretRef(passwordSecret)}"
            """.trimIndent().lines()
            val lines = mockBuilder.generatedLines()
            lines shouldContainExactly expectedLines
        }

        "generate blocks for multiple registries" {
            val usernameSecret = MockConfigFileBuilder.createSecret("registryUser")
            val passwordSecret1 = MockConfigFileBuilder.createSecret("registryPass1")
            val passwordSecret2 = MockConfigFileBuilder.createSecret("registryPass2")
            val definitions = listOf(
                YarnDefinition(
                    MockConfigFileBuilder.createInfrastructureService(REGISTRY_URI, usernameSecret, passwordSecret1),
                    null,
                    true,
                    YarnAuthMode.AUTH_IDENT
                ),
                YarnDefinition(
                    MockConfigFileBuilder.createInfrastructureService(
                        REGISTRY_URI + "1",
                        usernameSecret,
                        passwordSecret2
                    ),
                    null,
                    true,
                    YarnAuthMode.AUTH_IDENT
                )
            )

            val mockBuilder = MockConfigFileBuilder()

            YarnRcGenerator().generate(mockBuilder.builder, definitions)

            val expectedLines = """
                npmRegistries:
                  "$REGISTRY_URI":
                    npmAlwaysAuth: true
                    npmAuthIdent: "${MockConfigFileBuilder.testSecretRef(usernameSecret)}:${MockConfigFileBuilder.testSecretRef(passwordSecret1)}"

                  "${REGISTRY_URI}1":
                    npmAlwaysAuth: true
                    npmAuthIdent: "${MockConfigFileBuilder.testSecretRef(usernameSecret)}:${MockConfigFileBuilder.testSecretRef(passwordSecret2)}"
            """.trimIndent().lines()
            val lines = mockBuilder.generatedLines()

            lines shouldContainExactly expectedLines
        }

        "generate a block for a registry with a token authentication" {
            val usernameSecret = MockConfigFileBuilder.createSecret("usernameSecret")
            val passwordSecret = MockConfigFileBuilder.createSecret("registryToken")
            val definition = YarnDefinition(
                MockConfigFileBuilder.createInfrastructureService(REGISTRY_URI, usernameSecret, passwordSecret),
                null,
                true,
                YarnAuthMode.AUTH_TOKEN
            )

            val mockBuilder = MockConfigFileBuilder()

            YarnRcGenerator().generate(mockBuilder.builder, listOf(definition))

            val expectedLines = """
                npmRegistries:
                  "$REGISTRY_URI":
                    npmAlwaysAuth: true
                    npmAuthToken: "${MockConfigFileBuilder.testSecretRef(passwordSecret)}"
            """.trimIndent().lines()
            val lines = mockBuilder.generatedLines()
            lines shouldContainExactly expectedLines
        }

        "skip the npmAlwaysAuth line if the flag is false" {
            val usernameSecret = MockConfigFileBuilder.createSecret("usernameSecret")
            val passwordSecret = MockConfigFileBuilder.createSecret("registryToken")
            val definition = YarnDefinition(
                MockConfigFileBuilder.createInfrastructureService(REGISTRY_URI, usernameSecret, passwordSecret),
                null,
                false,
                YarnAuthMode.AUTH_TOKEN
            )

            val mockBuilder = MockConfigFileBuilder()

            YarnRcGenerator().generate(mockBuilder.builder, listOf(definition))

            val expectedLines = """
                npmRegistries:
                  "$REGISTRY_URI":
                    npmAuthToken: "${MockConfigFileBuilder.testSecretRef(passwordSecret)}"
            """.trimIndent().lines()
            val lines = mockBuilder.generatedLines()
            lines shouldContainExactly expectedLines
        }

        "generate proxy configuration" {
            val proxyHttp = "http://proxy.example.com:8080"
            val proxyHttps = "https://proxy2.example.com:8080"
            val environment = mapOf(
                "HTTP_PROXY" to proxyHttp,
                "HTTPS_PROXY" to proxyHttps
            )

            val definition = YarnDefinition(
                MockConfigFileBuilder.createInfrastructureService(REGISTRY_URI),
                null
            )

            val mockBuilder = MockConfigFileBuilder()

            withEnvironment(environment, OverrideMode.SetOrOverride) {
                YarnRcGenerator().generate(mockBuilder.builder, listOf(definition))
            }

            mockBuilder.generatedLines().take(3) shouldContainExactly listOf(
                "httpProxy: \"$proxyHttp\"",
                "httpsProxy: \"$proxyHttps\"",
                ""
            )
        }

        "drop undefined settings from the proxy configuration" {
            val proxyHttps = "https://sec-proxy.example.com:8080"
            val environment = mapOf(
                "HTTPS_PROXY" to proxyHttps
            )

            val definition = YarnDefinition(
                MockConfigFileBuilder.createInfrastructureService(REGISTRY_URI),
                null
            )

            val mockBuilder = MockConfigFileBuilder()

            withEnvironment(environment, OverrideMode.SetOrOverride) {
                YarnRcGenerator().generate(mockBuilder.builder, listOf(definition))
            }

            mockBuilder.generatedLines().take(2) shouldContainExactly listOf(
                "httpsProxy: \"$proxyHttps\"",
                ""
            )
        }
    }
})

const val REGISTRY_URI = "https://registry.example.org/_packaging/test/npm/registry/"
