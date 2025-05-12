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

import org.eclipse.apoapsis.ortserver.workers.common.env.ConanGenerator
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.ConanDefinition

class ConanGeneratorTest : WordSpec({
    "environmentDefinitionType" should {
        "return the correct definition class" {
            val definitionType = ConanGenerator().environmentDefinitionType

            definitionType shouldBe ConanDefinition::class.java
        }
    }

    "generate" should {
        "generate the file at the correct location" {
            val definition = ConanDefinition(
                MockConfigFileBuilder.createInfrastructureService(REMOTE_URL),
                emptySet(),
                REMOTE_NAME,
                REMOTE_URL,
                true
            )

            val mockBuilder = MockConfigFileBuilder()

            ConanGenerator().generate(mockBuilder.builder, listOf(definition))

            mockBuilder.homeFileName shouldBe ".conan/remotes.json"
        }

        "generate a file with a single remote" {
            val definition = ConanDefinition(
                MockConfigFileBuilder.createInfrastructureService(REMOTE_URL),
                emptySet(),
                REMOTE_NAME,
                REMOTE_URL,
                true
            )

            val mockBuilder = MockConfigFileBuilder()

            ConanGenerator().generate(mockBuilder.builder, listOf(definition))

            val expectedLines = listOf(
                "{",
                "  \"remotes\": [",
                "    {",
                "      \"name\": \"$REMOTE_NAME\",",
                "      \"url\": \"$REMOTE_URL\",",
                "      \"verify_ssl\": true",
                "    }",
                "  ]",
                "}"
            )
            val lines = mockBuilder.generatedLines()
            lines shouldContainExactly expectedLines
        }

        "generate a file with several remotes" {
            val definitions = listOf(
                ConanDefinition(
                    MockConfigFileBuilder.createInfrastructureService(REMOTE_URL),
                    emptySet(),
                    REMOTE_NAME,
                    REMOTE_URL,
                    true
                ),
                ConanDefinition(
                    MockConfigFileBuilder.createInfrastructureService(REMOTE_URL + 1),
                    emptySet(),
                    REMOTE_NAME + 1,
                    REMOTE_URL + 1,
                    false
                )
            )

            val mockBuilder = MockConfigFileBuilder()

            ConanGenerator().generate(mockBuilder.builder, definitions)

            val expectedLines = listOf(
                "{",
                "  \"remotes\": [",
                "    {",
                "      \"name\": \"$REMOTE_NAME\",",
                "      \"url\": \"$REMOTE_URL\",",
                "      \"verify_ssl\": true",
                "    },",
                "    {",
                "      \"name\": \"${REMOTE_NAME + 1}\",",
                "      \"url\": \"${REMOTE_URL + 1}\",",
                "      \"verify_ssl\": false",
                "    }",
                "  ]",
                "}"
            )
            val lines = mockBuilder.generatedLines()
            lines shouldContainExactly expectedLines
        }

        "obtain the url from the service if not defined" {
            val definition = ConanDefinition(
                MockConfigFileBuilder.createInfrastructureService(REMOTE_URL),
                emptySet(),
                REMOTE_NAME,
                null,
                true
            )

            val mockBuilder = MockConfigFileBuilder()

            ConanGenerator().generate(mockBuilder.builder, listOf(definition))

            val expectedLines = listOf(
                "{",
                "  \"remotes\": [",
                "    {",
                "      \"name\": \"$REMOTE_NAME\",",
                "      \"url\": \"$REMOTE_URL\",",
                "      \"verify_ssl\": true",
                "    }",
                "  ]",
                "}"
            )
            val lines = mockBuilder.generatedLines()
            lines shouldContainExactly expectedLines
        }

        "allow overriding the service URL in the definition" {
            val definition = ConanDefinition(
                MockConfigFileBuilder.createInfrastructureService("https://example.com"),
                emptySet(),
                REMOTE_NAME,
                REMOTE_URL,
                true
            )

            val mockBuilder = MockConfigFileBuilder()

            ConanGenerator().generate(mockBuilder.builder, listOf(definition))

            val expectedLines = listOf(
                "{",
                "  \"remotes\": [",
                "    {",
                "      \"name\": \"$REMOTE_NAME\",",
                "      \"url\": \"$REMOTE_URL\",",
                "      \"verify_ssl\": true",
                "    }",
                "  ]",
                "}"
            )
            val lines = mockBuilder.generatedLines()
            lines shouldContainExactly expectedLines
        }
    }
})

const val REMOTE_NAME = "conancenter"
const val REMOTE_URL = "https://center.conan.io"
