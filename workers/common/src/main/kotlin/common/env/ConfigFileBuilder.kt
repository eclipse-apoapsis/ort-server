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

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

import kotlin.random.Random

import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext

/**
 * A helper class supporting the generation of configuration files.
 *
 * This class can be used by concrete generator classes to generate package manager-specific configuration files.
 * The class exposes a [PrintWriter] for generating arbitrary content. It offers some special support for adding the
 * values of secrets to configuration files: This can be done by requesting a [secretRef] for a specific [Secret].
 * This reference is later replaced by the actual value of the secret.
 *
 * Implementation notes:
 * - This class is not thread-safe; a single instance should be used only by a single generator at a time.
 * - The implementation expects that configuration files are not big; therefore, the whole content of the file is
 *   kept in memory before it is written to disk.
 */
class ConfigFileBuilder(val context: WorkerContext) {
    companion object {
        /**
         * Print the given [multiLineText] making sure that the correct line endings are used. This function is
         * intended to be used with a Kotlin multiline string. In multiline strings line endings are always
         * represented by single newline characters. This function replaces this character with the platform-specific
         * newline character.
         */
        fun PrintWriter.printLines(multiLineText: String) {
            println(multiLineText.replace("\n", System.lineSeparator()))
        }

        /**
         * Generate a unique name for a secret reference. The name is based on a random number. Therefore, it should
         * not appear in other parts of the generated file, and no escaping needs to be implemented.
         */
        private fun generateReference(): String = "#{${Random.nextLong()}}"
    }

    /** A map storing the secret references used in the generated file. */
    private val secretReferences = mutableMapOf<String, Secret>()

    /**
     * Generate a configuration file at the location defined by the given [file] with content defined by the
     * given [block].
     */
    suspend fun build(file: File, block: suspend PrintWriter.() -> Unit) {
        val writer = StringWriter()
        val printWriter = PrintWriter(writer)

        printWriter.block()

        val secretValues = context.resolveSecrets(*secretReferences.values.toTypedArray())
        val content = secretReferences.entries.fold(writer.toString()) { text, entry ->
            text.replace(entry.key, secretValues.getValue(entry.value))
        }

        file.writeText(content)
    }

    /**
     * Generate a configuration in the current user's home directory with the given [name] with content defined by the
     * given [block].
     */
    suspend fun buildInUserHome(name: String, block: suspend PrintWriter.() -> Unit) {
        val file = File(System.getProperty("user.home"), name)

        build(file, block)
    }

    /**
     * Return a string-based reference to the given [secret]. This reference is replaced by the value of this
     * secret when the file is written.
     */
    fun secretRef(secret: Secret): String {
        val ref = generateReference()
        secretReferences[ref] = secret

        return ref
    }
}
