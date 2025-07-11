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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder
import org.eclipse.apoapsis.ortserver.workers.common.env.NuGetGenerator
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.NuGetAuthMode
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.NuGetDefinition

class NuGetGeneratorTest : WordSpec({
    "environmentDefinitionType" should {
        "return the correct definition class" {
            val definitionType = NuGetGenerator().environmentDefinitionType

            definitionType shouldBe NuGetDefinition::class.java
        }
    }

    "generate" should {
        "generate the file at the correct location" {
            val definition = NuGetDefinition(
                MockConfigFileBuilder.createInfrastructureService(REPOSITORY_URI),
                emptySet(),
                REPOSITORY_NAME,
                REPOSITORY_URI,
                REPOSITORY_PROTOCOL_VERSION
            )

            val mockBuilder = MockConfigFileBuilder()

            NuGetGenerator().generate(mockBuilder.builder, listOf(definition))

            mockBuilder.homeFileName shouldBe ".nuget/NuGet/NuGet.Config"
        }

        "generate a block for a repository with API key authentication" {
            val usernameSecret = MockConfigFileBuilder.createSecret("repositoryUser")
            val passwordSecret = MockConfigFileBuilder.createSecret("repositoryApiKey")
            val definition = NuGetDefinition(
                MockConfigFileBuilder.createInfrastructureService(REPOSITORY_URI, usernameSecret, passwordSecret),
                emptySet(),
                REPOSITORY_NAME,
                REPOSITORY_URI,
                REPOSITORY_PROTOCOL_VERSION
            )

            val mockBuilder = MockConfigFileBuilder()

            NuGetGenerator().generate(mockBuilder.builder, listOf(definition))

            val expectedLines = """
                <?xml version="1.0" encoding="utf-8"?>
                <configuration>
                  <packageSources>
                    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" protocolVersion="3" />
                  </packageSources>

                  <packageSourceCredentials>
                  </packageSourceCredentials>

                  <apikeys>
                    <add key=https://api.nuget.org/v3/index.json value="${MockConfigFileBuilder.testSecretRef(passwordSecret)}" />
                  </apikeys>
                </configuration>
            """.trimIndent().lines()
            val lines = mockBuilder.generatedLines()
            lines shouldContainExactly expectedLines
        }

        "generate blocks for multiple repositories with different authentication types" {
            val usernameSecret = MockConfigFileBuilder.createSecret("repositoryUser")
            val passwordSecret1 = MockConfigFileBuilder.createSecret("repositoryApiKey1")
            val passwordSecret2 = MockConfigFileBuilder.createSecret("repositoryApiKey2")
            val passwordSecret3 = MockConfigFileBuilder.createSecret("repositoryApiKey3")
            val passwordSecret4 = MockConfigFileBuilder.createSecret("repositoryApiKey4")
            val definitions = listOf(
                NuGetDefinition(
                    MockConfigFileBuilder.createInfrastructureService(REPOSITORY_URI, usernameSecret, passwordSecret1),
                    emptySet(),
                    REPOSITORY_NAME,
                    REPOSITORY_URI,
                    REPOSITORY_PROTOCOL_VERSION
                ),
                NuGetDefinition(
                    MockConfigFileBuilder.createInfrastructureService(REPOSITORY_URI, usernameSecret, passwordSecret2),
                    emptySet(),
                    REPOSITORY_NAME + "1",
                    REPOSITORY_URI + "1",
                    REPOSITORY_PROTOCOL_VERSION,
                    NuGetAuthMode.PASSWORD
                ),
                NuGetDefinition(
                    MockConfigFileBuilder.createInfrastructureService(REPOSITORY_URI, usernameSecret, passwordSecret3),
                    emptySet(),
                    REPOSITORY_NAME + "2",
                    REPOSITORY_URI + "2",
                    REPOSITORY_PROTOCOL_VERSION,
                    NuGetAuthMode.PASSWORD
                ),
                NuGetDefinition(
                    MockConfigFileBuilder.createInfrastructureService(REPOSITORY_URI, usernameSecret, passwordSecret4),
                    emptySet(),
                    REPOSITORY_NAME + "3",
                    REPOSITORY_URI + "3",
                    REPOSITORY_PROTOCOL_VERSION,
                    NuGetAuthMode.API_KEY
                )
            )

            val mockBuilder = MockConfigFileBuilder()

            NuGetGenerator().generate(mockBuilder.builder, definitions)

            val expectedLines = """
                <?xml version="1.0" encoding="utf-8"?>
                <configuration>
                  <packageSources>
                    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" protocolVersion="3" />
                    <add key="nuget.org1" value="https://api.nuget.org/v3/index.json1" protocolVersion="3" />
                    <add key="nuget.org2" value="https://api.nuget.org/v3/index.json2" protocolVersion="3" />
                    <add key="nuget.org3" value="https://api.nuget.org/v3/index.json3" protocolVersion="3" />
                  </packageSources>

                  <packageSourceCredentials>
                    <nuget.org1>
                      <add key="Username" value="${MockConfigFileBuilder.testSecretRef(usernameSecret)}" />
                      <add key="ClearTextPassword" value="${MockConfigFileBuilder.testSecretRef(passwordSecret2)}" />
                    </nuget.org1>
                    <nuget.org2>
                      <add key="Username" value="${MockConfigFileBuilder.testSecretRef(usernameSecret)}" />
                      <add key="ClearTextPassword" value="${MockConfigFileBuilder.testSecretRef(passwordSecret3)}" />
                    </nuget.org2>
                  </packageSourceCredentials>

                  <apikeys>
                    <add key=https://api.nuget.org/v3/index.json value="${MockConfigFileBuilder.testSecretRef(passwordSecret1)}" />
                    <add key=https://api.nuget.org/v3/index.json3 value="${MockConfigFileBuilder.testSecretRef(passwordSecret4)}" />
                  </apikeys>
                </configuration>
            """.trimIndent().lines()
            val lines = mockBuilder.generatedLines()

            lines shouldContainExactly expectedLines
        }

        "generate a block for a registry with a username and password authentication" {
            val usernameSecret = MockConfigFileBuilder.createSecret("repositoryUser")
            val passwordSecret = MockConfigFileBuilder.createSecret("repositoryPassword")
            val definition = NuGetDefinition(
                MockConfigFileBuilder.createInfrastructureService(REPOSITORY_URI, usernameSecret, passwordSecret),
                emptySet(),
                REPOSITORY_NAME,
                REPOSITORY_URI,
                REPOSITORY_PROTOCOL_VERSION,
                NuGetAuthMode.PASSWORD
            )

            val mockBuilder = MockConfigFileBuilder()

            NuGetGenerator().generate(mockBuilder.builder, listOf(definition))

            val expectedLines = """
                <?xml version="1.0" encoding="utf-8"?>
                <configuration>
                  <packageSources>
                    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" protocolVersion="3" />
                  </packageSources>

                  <packageSourceCredentials>
                    <nuget.org>
                      <add key="Username" value="${MockConfigFileBuilder.testSecretRef(usernameSecret)}" />
                      <add key="ClearTextPassword" value="${MockConfigFileBuilder.testSecretRef(passwordSecret)}" />
                    </nuget.org>
                  </packageSourceCredentials>

                  <apikeys>
                  </apikeys>
                </configuration>
            """.trimIndent().lines()
            val lines = mockBuilder.generatedLines()
            lines shouldContainExactly expectedLines
        }

        "skip the protocolVersion line if it is missing from the definition" {
            val usernameSecret = MockConfigFileBuilder.createSecret("repositoryUser")
            val passwordSecret = MockConfigFileBuilder.createSecret("repositoryPassword")
            val definition = NuGetDefinition(
                service = MockConfigFileBuilder
                    .createInfrastructureService(REPOSITORY_URI, usernameSecret, passwordSecret),
                sourceName = REPOSITORY_NAME,
                sourcePath = REPOSITORY_URI,
                authMode = NuGetAuthMode.PASSWORD,
                credentialsTypes = null
            )

            val mockBuilder = MockConfigFileBuilder()

            NuGetGenerator().generate(mockBuilder.builder, listOf(definition))

            val expectedLines = """
                <?xml version="1.0" encoding="utf-8"?>
                <configuration>
                  <packageSources>
                    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" />
                  </packageSources>

                  <packageSourceCredentials>
                    <nuget.org>
                      <add key="Username" value="${MockConfigFileBuilder.testSecretRef(usernameSecret)}" />
                      <add key="ClearTextPassword" value="${MockConfigFileBuilder.testSecretRef(passwordSecret)}" />
                    </nuget.org>
                  </packageSourceCredentials>

                  <apikeys>
                  </apikeys>
                </configuration>
            """.trimIndent().lines()
            val lines = mockBuilder.generatedLines()
            lines shouldContainExactly expectedLines
        }
    }
})

const val REPOSITORY_NAME = "nuget.org"
const val REPOSITORY_URI = "https://api.nuget.org/v3/index.json"
const val REPOSITORY_PROTOCOL_VERSION = "3"
