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

package org.eclipse.apoapsis.ortserver.workers.common.env

import io.kotest.common.runBlocking

import io.mockk.MockKAnswerScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

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
            userSecret: Secret = mockk(relaxed = true),
            passwordSecret: Secret = mockk(relaxed = true),
            credentialsTypes: Set<CredentialsType> = EnumSet.of(CredentialsType.NETRC_FILE)
        ): InfrastructureService =
            InfrastructureService(
                name = url,
                url = url,
                usernameSecret = userSecret,
                passwordSecret = passwordSecret,
                organization = null,
                product = null,
                credentialsTypes = credentialsTypes
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

    /** Stores information about the files that have been generated via this mock builder. */
    private val generatedFiles = mutableListOf<GeneratedFile>()

    /** The mock for the [WorkerContext] used by the mock [ConfigFileBuilder]. */
    val contextMock = mockk<WorkerContext>()

    /** A list of the files that have been generated via this mock builder's [ConfigFileBuilder.build] function. */
    val targetFiles: List<File>
        get() = generatedFiles.mapNotNull { it.targetFile }

    /** The path of the single configuration file generated via this builder. */
    val targetFile: File?
        get() = targetFiles.singleOrNull()

    /**
     * A list with the names of the files that have been generated via this mock builder's
     * [ConfigFileBuilder.buildInUserHome] function.
     */
    val homeFileNames: List<String>
        get() = generatedFiles.mapNotNull { it.homeFileName }

    /** The name of the single file generated in the user's home directory. */
    val homeFileName: String?
        get() = homeFileNames.singleOrNull()

    /** The mock [ConfigFileBuilder] provided by this class. */
    val builder = createBuilderMock()

    /**
     * Return the text of a configuration file that was generated using this mock builder specified by either
     * [targetFile] or [homeFileName]. Result is *null* if no matching file was found.
     */
    fun generatedTextFor(targetFile: File? = null, homeFileName: String? = null): String? =
        generatedFiles.firstOrNull { it.targetFile == targetFile && it.homeFileName == homeFileName }?.content

    /**
     * Return the single text lines that were generated using this mock builder for the specified [targetFile] or
     * [homeFileName]. Result is an empty list if no matching file was found.
     */
    fun generatedLinesFor(targetFile: File? = null, homeFileName: String? = null): List<String> {
        val lines = generatedTextFor(targetFile, homeFileName)?.split(System.lineSeparator()).orEmpty()

        // Remove a single last empty line caused by the last newline character in the generated content.
        return lines.takeIf { it.isEmpty() || it.last().isNotEmpty() } ?: lines.dropLast(1)
    }

    /**
     * Return the text of the single configuration file that was generated using the managed mock builder. Fail if
     * no file or multiple files were generated.
     */
    fun generatedText(): String = generatedFiles.single().content

    /**
     * Return the single text lines that were generated using the managed mock builder. Fail if no file or multiple
     * files were generated.
     */
    fun generatedLines(): List<String> {
        val generatedFile = generatedFiles.single()
        return generatedLinesFor(generatedFile.targetFile, generatedFile.homeFileName)
    }

    /**
     * Create a mock for a [ConfigFileBuilder] that is prepared to record its invocations.
     */
    private fun createBuilderMock(): ConfigFileBuilder =
        mockk {
            coEvery { build(any(), any()) } answers {
                invokeBuilderBlock(firstArg<File>(), null)
            }

            coEvery { buildInUserHome(any(), any()) } answers {
                invokeBuilderBlock(null, firstArg<String>())
            }

            every { secretRef(any()) } answers {
                testSecretRef(firstArg())
            }

            every { context } returns contextMock
        }

    /**
     * Invoke the block passed to a build() function of the mock builder, so that the generated text can be recorded
     * for the specified [targetFile] or [homeFileName].
     */
    private fun MockKAnswerScope<Unit, Unit>.invokeBuilderBlock(targetFile: File?, homeFileName: String?) {
        val writer = StringWriter()
        val block = secondArg<suspend PrintWriter.() -> Unit>()

        val printWriter = PrintWriter(writer)
        runBlocking { printWriter.block() }
        generatedFiles += GeneratedFile(targetFile, homeFileName, writer.toString())
    }
}

/**
 * A data class holding information about a file generated via [MockConfigFileBuilder].
 */
private data class GeneratedFile(
    /** The path to the generated file if [ConfigFileBuilder.build] was called. */
    val targetFile: File?,

    /** The name of the generated file if [ConfigFileBuilder.buildInUserHome] was called. */
    val homeFileName: String?,

    /** The content of the generated file. */
    val content: String
)
