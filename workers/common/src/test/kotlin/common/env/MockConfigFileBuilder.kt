/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.common.env

import io.kotest.common.runBlocking

import io.mockk.MockKAnswerScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.model.Secret

/**
 * A helper class for testing concrete environment generator classes and the configuration files they produce.
 * An instance of this class provides a fully prepared mock of a [ConfigFileBuilder] that can be passed to a
 * generator object under test. It is then possible to inspect the content that has been generated.
 *
 * References to secrets are generated in a deterministic way using the [testSecretRef] function. So, it can be
 * tested whether the generated content contains the expected secret values.
 */
class MockConfigFileBuilder {
    companion object {
        /** URL of a test repository. */
        const val REPOSITORY_URL = "https://repo.example.org/test-orga/test-repo.git"

        /**
         * Generate a string to reference the given [Secret]. This function is used by the mock config builder to
         * generate secret references. It can be used as well to verify the generated configuration file.
         */
        fun testSecretRef(secret: Secret): String = "#{${System.identityHashCode(secret)}}"

        /**
         * Return a test [InfrastructureService] based on the provided parameters.
         */
        fun createInfrastructureService(
            url: String = REPOSITORY_URL,
            userSecret: Secret = mockk(),
            passwordSecret: Secret = mockk()
        ): InfrastructureService =
            InfrastructureService(
                name = url,
                url = url,
                usernameSecret = userSecret,
                passwordSecret = passwordSecret,
                organization = null,
                product = null
            )

        /**
         * Return a test [Secret] based on the given [name].
         */
        fun createSecret(name: String): Secret =
            Secret(
                id = 0L,
                path = name,
                name = name,
                description = null,
                organization = null,
                product = null,
                repository = null
            )
    }

    /** The writer to store the generated text. */
    private val writer = StringWriter()

    /** The path of the generated configuration file. */
    var targetFile: File? = null

    /** The name of the file generated in the user's home directory. */
    var homeFileName: String? = null

    /** The mock [ConfigFileBuilder] provided by this class. */
    val builder = createBuilderMock()

    /**
     * Return the text that was generated using the managed mock builder.
     */
    fun generatedText(): String = writer.toString()

    /**
     * Return the single text lines that were generated using the managed mock builder.
     */
    fun generatedLines(): List<String> {
        val lines = generatedText().split(System.lineSeparator())

        // Remove a single last empty line caused by the last newline character in the generated content.
        return lines.takeIf { it.isEmpty() || it.last().isNotEmpty() } ?: lines.dropLast(1)
    }

    /**
     * Create a mock for a [ConfigFileBuilder] that is prepared to record its invocations.
     */
    private fun createBuilderMock(): ConfigFileBuilder =
        mockk {
            coEvery { build(any(), any()) } answers {
                targetFile = firstArg()
                invokeBuilderBlock()
            }

            coEvery { buildInUserHome(any(), any()) } answers {
                homeFileName = firstArg()
                invokeBuilderBlock()
            }

            every { secretRef(any()) } answers {
                testSecretRef(firstArg())
            }
        }

    /**
     * Invoke the block passed to a build() function of the mock builder, so that the generated text can be recorded.
     */
    private fun MockKAnswerScope<Unit, Unit>.invokeBuilderBlock() {
        val block = secondArg<suspend PrintWriter.() -> Unit>()

        val printWriter = PrintWriter(writer)
        runBlocking { printWriter.block() }
    }
}
