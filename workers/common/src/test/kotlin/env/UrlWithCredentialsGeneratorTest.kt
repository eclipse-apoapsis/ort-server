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

package org.eclipse.apoapsis.ortserver.workers.common.env

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.createInfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.createSecret
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.testSecretRef
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.BazelDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition

class UrlWithCredentialsGeneratorTest : StringSpec({
    "Files with correct names should be generated" {
        val mockBuilder = MockConfigFileBuilder()
        val definition = EnvironmentServiceDefinition(
            createInfrastructureService(
                "https://repo.example.org",
                createSecret("s1"),
                createSecret("s2"),
                EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
            )
        )

        UrlWithCredentialsGenerator(CredentialFile.GitCredentialsFile).generate(mockBuilder.builder, listOf(definition))

        mockBuilder.homeFileNames shouldContainExactlyInAnyOrder listOf(".git-credentials")
    }

    "Files should only be generated if services with Git credentials exist" {
        val mockBuilder = MockConfigFileBuilder()
        val definition = EnvironmentServiceDefinition(
            createInfrastructureService(
                "https://repo.example.org",
                createSecret("s1"),
                createSecret("s2")
            )
        )

        UrlWithCredentialsGenerator(CredentialFile.GitCredentialsFile).generate(mockBuilder.builder, listOf(definition))

        mockBuilder.homeFileNames should beEmpty()
    }

    "A correct .git-credentials file should be generated" {
        val mockBuilder = MockConfigFileBuilder()
        val secUser1 = createSecret("user1Secret")
        val secPass1 = createSecret("pass1Secret")
        val secUser2 = createSecret("user2Secret")
        val secPass2 = createSecret("pass2Secret")

        val definitions = listOf(
            EnvironmentServiceDefinition(
                createInfrastructureService(
                    "https://repo1.example.org",
                    secUser1,
                    secPass1
                ),
                credentialsTypes = EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
            ),
            EnvironmentServiceDefinition(
                createInfrastructureService(
                    "http://repo2.example.org:444/orga/repo.git",
                    secUser2,
                    secPass2,
                    credentialsTypes = EnumSet.allOf(CredentialsType::class.java)
                )
            )
        )

        UrlWithCredentialsGenerator(CredentialFile.GitCredentialsFile).generate(mockBuilder.builder, definitions)
        val lines = mockBuilder.generatedLinesFor(homeFileName = ".git-credentials")

        lines shouldContainExactlyInAnyOrder listOf(
            "https://${testSecretRef(secUser1)}:${testSecretRef(secPass1)}@repo1.example.org",
            "http://${testSecretRef(secUser2)}:${testSecretRef(secPass2)}@repo2.example.org:444/orga/repo.git"
        )
    }

    "Infrastructure service with invalid URLs should be ignored" {
        val mockBuilder = MockConfigFileBuilder()
        val secUser = createSecret("user1Secret")
        val secPass = createSecret("pass1Secret")

        val definitions = listOf(
            EnvironmentServiceDefinition(
                createInfrastructureService(
                    "?An invalid URL?!",
                    secUser,
                    secPass
                ),
                credentialsTypes = EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
            ),
            EnvironmentServiceDefinition(
                createInfrastructureService(
                    "https://repo.example.org/orga/repo.git",
                    secUser,
                    secPass,
                    credentialsTypes = EnumSet.allOf(CredentialsType::class.java)
                )
            )
        )

        UrlWithCredentialsGenerator(CredentialFile.GitCredentialsFile).generate(mockBuilder.builder, definitions)
        val lines = mockBuilder.generatedLinesFor(homeFileName = ".git-credentials")

        lines shouldContainExactly listOf(
            "https://${testSecretRef(secUser)}:${testSecretRef(secPass)}@repo.example.org/orga/repo.git"
        )
    }

    "Secret values should be URL-encoded" {
        val mockBuilder = MockConfigFileBuilder()
        val secUser = createSecret("user1Secret")
        val secPass = createSecret("pass1Secret")

        val definitions = listOf(
            EnvironmentServiceDefinition(
                createInfrastructureService(
                    "https://repo1.example.org",
                    secUser,
                    secPass
                ),
                credentialsTypes = EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
            )
        )

        UrlWithCredentialsGenerator(CredentialFile.GitCredentialsFile).generate(mockBuilder.builder, definitions)

        fun checkEncodingFun(reference: String) {
            val encodingFun = mockBuilder.encodingFunctionFor(reference)
            encodingFun("#+1/") shouldBe "%23%2B1%2F"
        }

        checkEncodingFun(testSecretRef(secUser))
        checkEncodingFun(testSecretRef(secPass))
    }

    "for Bazel, environmentDefinitionType" should {
        "return the correct definition class" {
            val definitionType = UrlWithCredentialsGenerator(
                CredentialFile.BazelCredentialsFile
            ).environmentDefinitionType

            definitionType shouldBe BazelDefinition::class.java
        }
    }

    "for Bazel, generate" should {
        "generate the files at the correct location" {
            val definition = BazelDefinition(
                MockConfigFileBuilder.createInfrastructureService(REMOTE_URL)
            )

            val mockBuilder = MockConfigFileBuilder()

            UrlWithCredentialsGenerator(
                CredentialFile.BazelCredentialsFile
            ).generate(mockBuilder.builder, listOf(definition))

            mockBuilder.homeFileNames shouldBe listOf(CredentialFile.BazelCredentialsFile.fileName)
        }

        "generate a file with a single credential entry" {
            val mockBuilder = MockConfigFileBuilder()
            val secUser1 = MockConfigFileBuilder.createSecret("user1Secret")
            val secPass1 = MockConfigFileBuilder.createSecret("pass1Secret")
            val infrastructureService = createInfrastructureService(
                "https://repo1.example.org",
                secUser1,
                secPass1
            )
            val definition = BazelDefinition(infrastructureService)

            UrlWithCredentialsGenerator(
                CredentialFile.BazelCredentialsFile
            ).generate(mockBuilder.builder, listOf(definition))

            val expectedLines = """
                https://${testSecretRef(secUser1)}:${testSecretRef(secPass1)}@repo1.example.org
            """.trimIndent().lines()
            val lines = mockBuilder.generatedLinesFor(homeFileName = CredentialFile.BazelCredentialsFile.fileName)
            lines shouldContainExactly expectedLines
        }

        "generate a file with multiple credential entries" {
            val mockBuilder = MockConfigFileBuilder()
            val secUser1 = MockConfigFileBuilder.createSecret("user1Secret")
            val secPass1 = MockConfigFileBuilder.createSecret("pass1Secret")
            val infrastructureService1 = createInfrastructureService(
                "https://repo1.example.org",
                secUser1,
                secPass1
            )
            val definition1 = BazelDefinition(infrastructureService1)
            val secUser2 = MockConfigFileBuilder.createSecret("user2Secret")
            val secPass2 = MockConfigFileBuilder.createSecret("pass2Secret")
            val infrastructureService2 = createInfrastructureService(
                "https://repo2.example.org",
                secUser2,
                secPass2
            )
            val definition2 = BazelDefinition(infrastructureService2)

            UrlWithCredentialsGenerator(
                CredentialFile.BazelCredentialsFile
            ).generate(mockBuilder.builder, listOf(definition1, definition2))

            val expectedLines = """
                https://${testSecretRef(secUser1)}:${testSecretRef(secPass1)}@repo1.example.org
                https://${testSecretRef(secUser2)}:${testSecretRef(secPass2)}@repo2.example.org
            """.trimIndent().lines()
            val lines = mockBuilder.generatedLinesFor(homeFileName = CredentialFile.BazelCredentialsFile.fileName)
            lines shouldContainExactly expectedLines
        }
    }
})
