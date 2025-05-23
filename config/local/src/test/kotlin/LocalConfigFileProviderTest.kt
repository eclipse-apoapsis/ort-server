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

package org.eclipse.apoapsis.ortserver.config.local

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.config.local.LocalConfigFileProvider.Companion.CONFIG_DIR

private val CONFIG_PATH = Path("config-file")
private const val CONTENT = "content"

class LocalConfigFileProviderTest : WordSpec({
    "create" should {
        "successfully create a provider instance" {
            val directory = tempdir()
            directory.resolve(CONFIG_PATH.path).createNewFile()

            val config = ConfigFactory.parseMap(
                mapOf(
                    CONFIG_DIR to directory.absolutePath
                )
            )

            val provider = LocalConfigFileProvider.create(config)

            provider.contains(Context(""), CONFIG_PATH) shouldBe true
        }
    }

    "resolveContext" should {
        "always resolve to an empty context" {
            val provider = LocalConfigFileProvider(tempdir())

            provider.resolveContext(Context("")).name shouldBe ""
            provider.resolveContext(Context("context")).name shouldBe ""
        }
    }

    "getFile" should {
        "successfully provide a file" {
            val directory = tempdir()
            val file = directory.resolve(CONFIG_PATH.path)
            file.writeText(CONTENT)
            val provider = LocalConfigFileProvider(directory)

            val fileContent = provider.getFile(Context(""), CONFIG_PATH)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }

            fileContent shouldBe CONTENT
        }

        "throw an exception if the file cannot be found" {
            val directory = tempdir()
            val provider = LocalConfigFileProvider(directory)

            shouldThrow<ConfigException> {
                provider.getFile(Context(""), CONFIG_PATH)
            }
        }

        "throw an exception if the path refers a directory" {
            val directory = tempdir()
            directory.resolve(CONFIG_PATH.path).mkdir()
            val provider = LocalConfigFileProvider(directory)

            shouldThrow<ConfigException> {
                provider.getFile(Context(""), CONFIG_PATH)
            }
        }
    }

    "contains" should {
        "return `true` if the config file is present" {
            val directory = tempdir()
            directory.resolve(CONFIG_PATH.path).createNewFile()
            val provider = LocalConfigFileProvider(directory)

            provider.contains(Context(""), CONFIG_PATH) shouldBe true
        }

        "return `false` if the path refers to a directory" {
            val directory = tempdir()
            directory.resolve(CONFIG_PATH.path).mkdir()
            val provider = LocalConfigFileProvider(directory)

            provider.contains(Context(""), CONFIG_PATH) shouldBe false
        }

        "return `false` if a the file cannot be found" {
            val directory = tempdir()
            val provider = LocalConfigFileProvider(directory)

            provider.contains(Context(""), CONFIG_PATH) shouldBe false
        }
    }

    "listFiles" should {
        "return a list of files inside a given directory" {
            val directory = tempdir()
            val files = listOf("file-1", "file-2", "file-3")
            files.forEach { directory.resolve(it).createNewFile() }
            val provider = LocalConfigFileProvider(directory)

            val listFiles = provider.listFiles(Context(""), Path(""))

            val expectedFiles = files.map { Path(directory.resolve(it).absolutePath) }
            listFiles shouldContainExactlyInAnyOrder expectedFiles
        }

        "throw an exception if the path does not exist" {
            val directory = tempdir()
            val provider = LocalConfigFileProvider(directory)

            shouldThrow<ConfigException> {
                provider.listFiles(Context(""), CONFIG_PATH)
            }
        }

        "throw an exception if the path does not refer a directory" {
            val directory = tempdir()
            directory.resolve(CONFIG_PATH.path).createNewFile()
            val provider = LocalConfigFileProvider(directory)

            shouldThrow<ConfigException> {
                provider.listFiles(Context(""), CONFIG_PATH)
            }
        }
    }
})
