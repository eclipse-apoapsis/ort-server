/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.common.env

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.createInfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.createSecret
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition

class GitConfigGeneratorTest : WordSpec({
    "Git config file .gitconfig" should {
        "be generated" {
            val mockBuilder = MockConfigFileBuilder()
            val definitions = emptyList<EnvironmentServiceDefinition>()
            val parsedConfig = mapOf(
                "https://github.com" to "ssh://git@github.com",
                "https://github.com/" to "git@github.com:"
            )

            GitConfigGenerator.generateGitConfig(mockBuilder.builder, definitions, parsedConfig)

            mockBuilder.homeFileNames shouldContainExactlyInAnyOrder listOf(".gitconfig")

            val lines = mockBuilder.generatedLinesFor(homeFileName = ".gitconfig")

            lines shouldContainExactly listOf(
                "[url \"https://github.com\"]",
                "\tinsteadOf = \"ssh://git@github.com\"",
                "[url \"https://github.com/\"]",
                "\tinsteadOf = \"git@github.com:\""
            )
        }

        "only be generated if services with Git configs exist" {
            val mockBuilder = MockConfigFileBuilder()
            val definitions = emptyList<EnvironmentServiceDefinition>()
            val parsedConfig = emptyMap<String, String>()

            GitConfigGenerator.generateGitConfig(mockBuilder.builder, definitions, parsedConfig)

            mockBuilder.homeFileNames should beEmpty()
        }

        "be generated if there are any Git credentials defined" {
            val mockBuilder = MockConfigFileBuilder()
            val definitions = listOf(
                EnvironmentServiceDefinition(
                    createInfrastructureService(
                        "https://repo.example.org",
                        createSecret("s1"),
                        createSecret("s2"),
                        EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
                    )
                )
            )
            val parsedConfig = emptyMap<String, String>()

            GitConfigGenerator.generateGitConfig(mockBuilder.builder, definitions, parsedConfig)
            val lines = mockBuilder.generatedLinesFor(homeFileName = ".gitconfig")

            lines shouldContainExactly listOf(
                "[credential]",
                "\thelper = store"
            )
        }

        "be generated if there are any Git credentials defined and Git config definitions" {
            val mockBuilder = MockConfigFileBuilder()
            val definitions = listOf(
                EnvironmentServiceDefinition(
                    createInfrastructureService(
                        "https://repo.example.org",
                        createSecret("s1"),
                        createSecret("s2"),
                        EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
                    )
                )
            )
            val parsedConfig = mapOf(
                "https://github.com" to "ssh://git@github.com",
                "https://github.com/" to "git@github.com:"
            )

            GitConfigGenerator.generateGitConfig(mockBuilder.builder, definitions, parsedConfig)
            val lines = mockBuilder.generatedLinesFor(homeFileName = ".gitconfig")

            lines shouldContainExactly listOf(
                "[credential]",
                "\thelper = store",
                "[url \"https://github.com\"]",
                "\tinsteadOf = \"ssh://git@github.com\"",
                "[url \"https://github.com/\"]",
                "\tinsteadOf = \"git@github.com:\""
            )
        }
    }

    "parseGitConfigUrlInsteadOf" should {
        "gracefully handle an empty configuration" {
            val configLine = ""

            val result = GitConfigGenerator.parseGitConfigUrlInsteadOf(configLine)

            result shouldBe emptyMap()
        }

        "gracefully handle a partly invalid configuration" {
            val configLine = "https://github.com*ssh://git@github.com,https://github.com/=git@github.com:"

            val result = GitConfigGenerator.parseGitConfigUrlInsteadOf(configLine)

            result shouldContainExactly mapOf(
                "https://github.com/" to "git@github.com:"
            )
        }

        "parse a syntactically correct configuration with 1 entry" {
            val configLine = " https://github.com = ssh://git@github.com "

            val result = GitConfigGenerator.parseGitConfigUrlInsteadOf(configLine)

            result shouldContainExactly mapOf(
                "https://github.com" to "ssh://git@github.com"
            )
        }

        "parse a syntactically correct configuration with 2 entries" {
            val configLine = " https://github.com = ssh://git@github.com , https://github.com/ = git@github.com: "

            val result = GitConfigGenerator.parseGitConfigUrlInsteadOf(configLine)

            result shouldContainExactly mapOf(
                "https://github.com" to "ssh://git@github.com",
                "https://github.com/" to "git@github.com:"
            )
        }
    }
})
