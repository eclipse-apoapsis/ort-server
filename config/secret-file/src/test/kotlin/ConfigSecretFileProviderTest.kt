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

package org.eclipse.apoapsis.ortserver.config.secret.file

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.io.File
import java.io.IOException

import org.eclipse.apoapsis.ortserver.config.Path

class ConfigSecretFileProviderTest : StringSpec({
    fun createSecretsFile(content: String): File =
        tempfile().also { it.writeText(content) }

    "A correct secret value should be returned" {
        val secretFile = createSecretsFile(SECRETS_FILE_CONTENT)

        val provider = ConfigSecretFileProvider(listOf(secretFile))
        val secret = provider.getSecret(Path("secret1"))

        secret shouldBe "value1"
    }

    "Keys and values should be trimmed correctly" {
        val secretFile = createSecretsFile(SECRETS_FILE_CONTENT)

        val provider = ConfigSecretFileProvider(listOf(secretFile))
        val secret = provider.getSecret(Path("secret2"))

        secret shouldBe "value2"
    }

    "An empty secret value should be returned" {
        val secretFile = createSecretsFile(SECRETS_FILE_CONTENT)

        val provider = ConfigSecretFileProvider(listOf(secretFile))
        val secret = provider.getSecret(Path("secret3"))

        secret shouldBe ""
    }

    "An exception is thrown for an unresolvable secret" {
        val secretName = "secretInvalid"
        val secretFile = createSecretsFile(SECRETS_FILE_CONTENT)

        val provider = ConfigSecretFileProvider(listOf(secretFile))

        val exception = shouldThrow<NoSuchElementException> {
            provider.getSecret(Path(secretName))
        }

        exception.message shouldContain secretName
    }

    "Multiple files should be searched" {
        val file1 = createSecretsFile("foo=bar")
        val file2 = createSecretsFile(SECRETS_FILE_CONTENT)

        val provider = ConfigSecretFileProvider(listOf(file1, file2))
        val secret = provider.getSecret(Path("secret1"))

        secret shouldBe "value1"
    }

    "Secret values from a file with a lower index in the list should take priority over values from later files" {
        val secretName = "secret1"
        val secretValue = "overridden value"
        val file1 = createSecretsFile("$secretName=$secretValue")
        val file2 = createSecretsFile(SECRETS_FILE_CONTENT)

        val provider = ConfigSecretFileProvider(listOf(file1, file2))
        val secret = provider.getSecret(Path("secret1"))

        secret shouldBe secretValue
    }

    "Secret values containing the key-value separator should be handled correctly" {
        val secretName = "secretWithComplexValue"
        val secretValue = "top=secret"
        val file = createSecretsFile("$secretName=$secretValue")

        val provider = ConfigSecretFileProvider(listOf(file))
        val secret = provider.getSecret(Path(secretName))

        secret shouldBe secretValue
    }

    "A non-existing file should cause an IOException" {
        val provider = ConfigSecretFileProvider(listOf(File("non-existing-file.sec")))

        shouldThrow<IOException> {
            provider.getSecret(Path("doesNotMatter"))
        }
    }
})

/** The content of a test file with some secret definitions. */
private val SECRETS_FILE_CONTENT = """
                # Test secrets
                secret1=value1
                secret2   =      value2  
                secret3=
                
                secretInvalid
            """.trimIndent()
