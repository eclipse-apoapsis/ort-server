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

package org.eclipse.apoapsis.ortserver.config.git

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path

internal const val GIT_URL = "https://github.com/doubleopen-project/ort-config-test.git"
internal const val GIT_BRANCH_MAIN = "main"
private const val GIT_BRANCH_DEV = "dev"

class GitConfigFileProviderTest : WordSpec({
    "resolveContext" should {
        "resolve an empty context successfully to HEAD of default branch" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(""))

            context.name shouldBe "5c2d08c40dc558962a3941855cba876066f6b4b9"
        }

        "resolve a context successfully to HEAD of the `main` branch" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            context.name shouldBe "5c2d08c40dc558962a3941855cba876066f6b4b9"
        }

        "resolve a context successfully to HEAD of the `dev` branch" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_DEV))

            context.name shouldBe "c7c011911baa064bef049c88807c4503fbe957c0"
        }
    }

    "contains" should {
        "return `true` if a file from root is present" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            provider.contains(context, Path("copyright-garbage.yml")) shouldBe true
        }

        "return `true` if a file from a subdirectory is present" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            provider.contains(context, Path("customer1/product1/evaluator.rules.kts")) shouldBe true
        }

        "return `true` if a file referred to by a symlink is present" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            provider.contains(context, Path("customer1/product1/copyright-garbage.yml")) shouldBe true
        }

        "return `true`if a file from a submodule is present" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            provider.contains(context, Path("ort-config-test-sm/license-classifications.yml")) shouldBe true
        }

        "return `true` if a file from a submodule, referred to by a symlink is present" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            provider.contains(context, Path("customer1/product1/license-classifications.yml")) shouldBe true
        }

        "return `false` if the path refers to a directory" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            provider.contains(context, Path("customer1/")) shouldBe false
        }

        "return `false` if a the file cannot be found" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            provider.contains(context, Path("non/existent/file")) shouldBe false
        }
    }

    "listFiles" should {
        "return a list of files inside a given directory" {
            val filesPath = "customer2/product2"
            val expectedFiles =
                listOf("copyright-garbage.yml", "evaluator.rules.kts", "license-classifications.yml")
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            val listFiles = provider.listFiles(context, Path(filesPath)).map { it.nameComponent }

            listFiles shouldContainExactlyInAnyOrder expectedFiles
        }

        "throw an exception if the path does not exist" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            shouldThrow<ConfigException> {
                provider.listFiles(context, Path("non/existent/path"))
            }
        }

        "throw an exception if the path does not refer a directory" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            shouldThrow<ConfigException> {
                provider.listFiles(context, Path("copyright-garbage.yml"))
            }
        }
    }

    "getFile" should {
        "successfully provide a file from `main` branch" {
            val content = "This is the main branch of the repository"
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            val fileContent = provider.getFile(context, Path("README.md"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }

            fileContent shouldBe content
        }

        "successfully provide a file from `dev` branch" {
            val content = "This is a dev branch of the repository"
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_DEV))

            val fileContent = provider.getFile(context, Path("README.md"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }

            fileContent shouldBe content
        }

        "throw an exception if the file cannot be found" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            shouldThrow<ConfigException> {
                provider.getFile(context, Path("README-non-existent.md"))
            }
        }

        "throw an exception if the path refers a directory" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            shouldThrow<ConfigException> {
                provider.getFile(context, Path("customer1/product1"))
            }
        }
    }
})
