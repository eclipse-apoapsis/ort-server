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

import java.io.IOException

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path

internal const val GIT_URL = "https://github.com/doubleopen-project/ort-config-test.git"

internal const val GIT_BRANCH_MAIN = "main"
private const val GIT_REVISION_MAIN = "5c2d08c40dc558962a3941855cba876066f6b4b9"
private val RESOLVED_CONTEXT_MAIN = Context(GIT_REVISION_MAIN)

private const val GIT_BRANCH_DEV = "dev"
private const val GIT_REVISION_DEV = "c7c011911baa064bef049c88807c4503fbe957c0"
private val RESOLVED_CONTEXT_DEV = Context(GIT_REVISION_DEV)

class GitConfigFileProviderTest : WordSpec({
    "resolveContext" should {
        "resolve an empty context successfully to HEAD of default branch" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(""))

            context.name shouldBe GIT_REVISION_MAIN
        }

        "resolve a context successfully to HEAD of the `main` branch" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_MAIN))

            context.name shouldBe GIT_REVISION_MAIN
        }

        "resolve a context successfully to HEAD of the `dev` branch" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())
            val context = provider.resolveContext(Context(GIT_BRANCH_DEV))

            context.name shouldBe GIT_REVISION_DEV
        }

        "throw an exception for a non-resolvable context" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            shouldThrow<IOException> {
                provider.resolveContext(Context("non-existent-branch"))
            }
        }
    }

    "contains" should {
        "return `true` if a file from root is present" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            provider.contains(RESOLVED_CONTEXT_MAIN, Path("copyright-garbage.yml")) shouldBe true
        }

        "return `true` if a file from a subdirectory is present" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            provider.contains(RESOLVED_CONTEXT_MAIN, Path("customer1/product1/evaluator.rules.kts")) shouldBe true
        }

        "return `true` if a file referred to by a symlink is present" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            provider.contains(RESOLVED_CONTEXT_MAIN, Path("customer1/product1/copyright-garbage.yml")) shouldBe true
        }

        "return `true`if a file from a submodule is present" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            provider.contains(
                RESOLVED_CONTEXT_MAIN,
                Path("ort-config-test-sm/license-classifications.yml")
            ) shouldBe true
        }

        "return `true` if a file from a submodule, referred to by a symlink is present" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            provider.contains(
                RESOLVED_CONTEXT_MAIN,
                Path("customer1/product1/license-classifications.yml")
            ) shouldBe true
        }

        "return `false` if the path refers to a directory" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            provider.contains(RESOLVED_CONTEXT_MAIN, Path("customer1")) shouldBe false
        }

        "return `true` if the path ending on a slash refers to a directory" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            provider.contains(RESOLVED_CONTEXT_MAIN, Path("customer1/")) shouldBe true
        }

        "return `false` if a the file cannot be found" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            provider.contains(RESOLVED_CONTEXT_MAIN, Path("non/existent/file")) shouldBe false
        }
    }

    "listFiles" should {
        "return a list of files inside a given directory" {
            val filesPath = "customer2/product2"
            val expectedFiles = listOf(
                "customer2/product2/copyright-garbage.yml",
                "customer2/product2/evaluator.rules.kts",
                "customer2/product2/license-classifications.yml"
            )
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            val listFiles = provider.listFiles(RESOLVED_CONTEXT_MAIN, Path(filesPath)).map { it.path }

            listFiles shouldContainExactlyInAnyOrder expectedFiles
        }

        "return a list of files inside a given directory if the paths end with a slash" {
            val filesPath = "customer2/product2/"
            val expectedFiles = listOf(
                "customer2/product2/copyright-garbage.yml",
                "customer2/product2/evaluator.rules.kts",
                "customer2/product2/license-classifications.yml"
            )
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            val listFiles = provider.listFiles(RESOLVED_CONTEXT_MAIN, Path(filesPath)).map { it.path }

            listFiles shouldContainExactlyInAnyOrder expectedFiles
        }

        "throw an exception if the path does not exist" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            shouldThrow<ConfigException> {
                provider.listFiles(RESOLVED_CONTEXT_MAIN, Path("non/existent/path"))
            }
        }

        "throw an exception if the path does not refer a directory" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            shouldThrow<ConfigException> {
                provider.listFiles(RESOLVED_CONTEXT_MAIN, Path("copyright-garbage.yml"))
            }
        }
    }

    "getFile" should {
        "successfully provide a file from `main` branch" {
            val content = "This is the main branch of the repository"
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            val fileContent = provider.getFile(RESOLVED_CONTEXT_MAIN, Path("README.md"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }

            fileContent shouldBe content
        }

        "successfully provide a file from `dev` branch" {
            val content = "This is a dev branch of the repository"
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            val fileContent = provider.getFile(RESOLVED_CONTEXT_DEV, Path("README.md"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }

            fileContent shouldBe content
        }

        "throw an exception if the file cannot be found" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            shouldThrow<ConfigException> {
                provider.getFile(RESOLVED_CONTEXT_MAIN, Path("README-non-existent.md"))
            }
        }

        "throw an exception if the path refers a directory" {
            val provider = GitConfigFileProvider(GIT_URL, tempdir())

            shouldThrow<ConfigException> {
                provider.getFile(RESOLVED_CONTEXT_MAIN, Path("customer1/product1"))
            }
        }
    }
})
