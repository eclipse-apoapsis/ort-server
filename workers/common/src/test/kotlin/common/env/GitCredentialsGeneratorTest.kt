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

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.createInfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.createSecret
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.testSecretRef
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition

class GitCredentialsGeneratorTest : StringSpec({
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

        GitCredentialsGenerator().generate(mockBuilder.builder, listOf(definition))

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

        GitCredentialsGenerator().generate(mockBuilder.builder, listOf(definition))

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

        GitCredentialsGenerator().generate(mockBuilder.builder, definitions)
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

        GitCredentialsGenerator().generate(mockBuilder.builder, definitions)
        val lines = mockBuilder.generatedLinesFor(homeFileName = ".git-credentials")

        lines shouldContainExactly listOf(
            "https://${testSecretRef(secUser)}:${testSecretRef(secPass)}@repo.example.org/orga/repo.git",
        )
    }
})
