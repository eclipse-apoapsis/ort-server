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

package org.ossreviewtoolkit.server.workers.common.env

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.engine.spec.tempfile
import io.kotest.extensions.system.OverrideMode
import io.kotest.extensions.system.withSystemProperties
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

import io.mockk.coEvery
import io.mockk.mockk

import java.util.Properties

import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext
import org.ossreviewtoolkit.server.workers.common.env.ConfigFileBuilder.Companion.printLines

class ConfigFileBuilderTest : StringSpec({
    "A PrintWriter is exposed" {
        val file = tempfile()

        val builder = ConfigFileBuilder(createContextMock())
        builder.build(file) {
            println("This is a line of text.")
            print("The answer is ")
            println(42)
        }

        file.readLines() shouldContainExactly listOf(
            "This is a line of text.",
            "The answer is 42"
        )
    }

    "Secret references are resolved automatically" {
        val file = tempfile()
        val secret1 = Secret(1, "p1", "s1", null, null, null, null)
        val secret2 = Secret(2, "p2", "s2", null, null, null, null)
        val secretValues = mapOf(secret1 to "value1", secret2 to "value2")

        val capturedSecrets = mutableListOf<Secret>()
        val context = mockk<WorkerContext> {
            coEvery { resolveSecrets(*varargAll { capturedSecrets.add(it) }) } returns secretValues
        }

        val builder = ConfigFileBuilder(context)
        builder.build(file) {
            println("secret1 = ${builder.secretRef(secret1)},")
            println("secret2 = ${builder.secretRef(secret2)}.")
        }

        file.readLines() shouldContainExactly listOf(
            "secret1 = value1,",
            "secret2 = value2."
        )

        capturedSecrets shouldContainExactlyInAnyOrder listOf(secret1, secret2)
    }

    "Multiline texts can be printed" {
        val file = tempfile()
        val content = """
            This is a file
            with multiple
            lines.
        """.trimIndent()

        val builder = ConfigFileBuilder(createContextMock())
        builder.build(file) {
            printLines(content)
        }

        file.readLines() shouldContainExactly listOf(
            "This is a file",
            "with multiple",
            "lines."
        )
    }

    "A file in the user's home directory can be generated" {
        val tempDir = tempdir()
        val fileName = "test.conf"
        val content = "test file content"
        val systemProperties = Properties().apply {
            setProperty("user.home", tempDir.absolutePath)
        }

        withSystemProperties(systemProperties, OverrideMode.SetOrOverride) {
            val builder = ConfigFileBuilder(createContextMock())
            builder.buildInUserHome(fileName) {
                printLines(content)
            }
        }

        val file = tempDir.resolve(fileName)
        file.readLines() shouldContainExactly listOf(content)
    }
})

/**
 * Create a mock for a [WorkerContext] and prepare it to return the given [secretValues] when asked to resolve
 * secrets.
 */
private fun createContextMock(secretValues: Map<Secret, String> = emptyMap()): WorkerContext =
    mockk<WorkerContext> {
        coEvery { resolveSecrets(*anyVararg()) } returns secretValues
    }
