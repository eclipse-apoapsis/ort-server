/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.createInfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.testSecretRef
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.BazelDefinition

class BazelGeneratorTest : WordSpec({
    "environmentDefinitionType" should {
        "return the correct definition class" {
            val definitionType = BazelGenerator().environmentDefinitionType

            definitionType shouldBe BazelDefinition::class.java
        }
    }

    "generate" should {
        "generate the files at the correct location" {
            val definition = BazelDefinition(
                MockConfigFileBuilder.createInfrastructureService(REMOTE_URL)
            )

            val mockBuilder = MockConfigFileBuilder()

            BazelGenerator().generate(mockBuilder.builder, listOf(definition))

            mockBuilder.homeFileNames shouldContainExactly listOf(BAZEL_CREDENTIALS_FILE_NAME, BAZEL_RC_FILE_NAME)
        }

        "generate a file with a single credential entry and a .bazelrc file" {
            val mockBuilder = MockConfigFileBuilder()
            val secUser1 = MockConfigFileBuilder.createSecret("user1Secret")
            val secPass1 = MockConfigFileBuilder.createSecret("pass1Secret")
            val infrastructureService = createInfrastructureService(
                "https://repo1.example.org",
                secUser1,
                secPass1
            )
            val definition = BazelDefinition(infrastructureService)

            BazelGenerator().generate(mockBuilder.builder, listOf(definition))

            val expectedLines = """
                https://${testSecretRef(secUser1)}:${testSecretRef(secPass1)}@repo1.example.org
            """.trimIndent().lines()
            val lines = mockBuilder.generatedLinesFor(homeFileName = BAZEL_CREDENTIALS_FILE_NAME)
            lines shouldContainExactly expectedLines

            mockBuilder.checkBazelRcFileContent()
        }

        "generate a file with multiple credential entries and a .bazelrc file" {
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

            BazelGenerator().generate(mockBuilder.builder, listOf(definition1, definition2))

            val expectedLines = """
                https://${testSecretRef(secUser1)}:${testSecretRef(secPass1)}@repo1.example.org
                https://${testSecretRef(secUser2)}:${testSecretRef(secPass2)}@repo2.example.org
            """.trimIndent().lines()
            val lines = mockBuilder.generatedLinesFor(homeFileName = BAZEL_CREDENTIALS_FILE_NAME)
            lines shouldContainExactly expectedLines

            mockBuilder.checkBazelRcFileContent()
        }
    }
})

private fun MockConfigFileBuilder.checkBazelRcFileContent() {
    val expectedLines = listOf(
        "common --credential_helper=/opt/bazel/bazel_cred_wrapper.sh"
    )
    val lines = generatedLinesFor(homeFileName = BAZEL_RC_FILE_NAME)
    lines shouldContainExactly expectedLines
}
