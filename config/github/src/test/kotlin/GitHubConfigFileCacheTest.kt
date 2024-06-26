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

package org.eclipse.apoapsis.ortserver.config.github

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

import io.ktor.utils.io.ByteReadChannel

import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

import kotlin.time.Duration.Companion.milliseconds

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GitHubConfigFileCacheTest : WordSpec({
    "getOrPutFile" should {
        "return the correct content of a file not yet present in the cache" {
            val cache = createCache()

            val stream = cache.getOrPutFile(revision(1), TEST_PATH, loadFileFunc())

            stream.verifyContent()
        }

        "return the correct content of a file already present in the cache" {
            val cache = createCache()
            val loadFunc = loadFileFunc()
            cache.getOrPutFile(revision(1), TEST_PATH, loadFunc).close()

            val stream = cache.getOrPutFile(revision(1), TEST_PATH, loadFunc)

            stream.verifyContent()
        }

        "support different revisions of a file" {
            val prefix = "otherRevision:\n"
            val cache = createCache()
            cache.getOrPutFile(revision(1), TEST_PATH, loadFileFunc()).close()

            val stream = cache.getOrPutFile(revision(2), TEST_PATH, loadFileFunc(prefix))

            stream.verifyContent(prefix)
        }

        "handle concurrent access" {
            val revisionCount = 4
            val accessCount = 16
            val cache = createCache()

            repeat(revisionCount) { revisionIndex ->
                val prefix = "rev$revisionIndex:\n"
                val loadFunc = loadFileFunc(prefix)

                repeat(accessCount) {
                    launch(Dispatchers.IO) {
                        val stream = cache.getOrPutFile(revision(revisionIndex), TEST_PATH, loadFunc)

                        stream.verifyContent(prefix)
                    }
                }
            }
        }
    }

    "getOrPutFolderContent" should {
        "return the correct content of a folder not yet present in the cache" {
            val name = "testFolder"
            val revision = revision(1)
            val cache = createCache()

            val content = cache.getOrPutFolderContent(revision, name, loadFolderContentFunc(name, revision))

            content shouldBe testFolderContent(name, revision)
        }

        "return the correct content of a folder already present in the cache" {
            val name = "testFolder"
            val revision = revision(1)
            val cache = createCache()
            val loadFunc = loadFolderContentFunc(name, revision)
            cache.getOrPutFolderContent(revision, name, loadFunc)

            val content = cache.getOrPutFolderContent(revision, name, loadFunc)

            content shouldBe testFolderContent(name, revision)
        }

        "support different folders in different revisions" {
            val folder1 = "someFolder"
            val folder2 = "anotherFolder"
            val revision1 = revision(1)
            val revision2 = revision(11)
            val cache = createCache()

            suspend fun checkFolderContent(folder: String, revision: String) {
                val content = cache.getOrPutFolderContent(revision, folder, loadFolderContentFunc(folder, revision))
                content shouldBe testFolderContent(folder, revision)
            }

            checkFolderContent(folder1, revision1)
            checkFolderContent(folder2, revision2)
            checkFolderContent(folder1, revision2)
            checkFolderContent(folder2, revision1)
        }

        "handle concurrent access" {
            val folder = "testFolder"
            val revisionCount = 4
            val accessCount = 16
            val cache = createCache()

            repeat(revisionCount) { revisionIndex ->
                val loadFunc = loadFolderContentFunc(folder, revision(revisionIndex))

                repeat(accessCount) {
                    launch(Dispatchers.IO) {
                        val content = cache.getOrPutFolderContent(revision(revisionIndex), folder, loadFunc)

                        content shouldBe testFolderContent(folder, revision(revisionIndex))
                    }
                }
            }
        }
    }
})

/** The interval to check for the availability of read locks. */
private val lockCheckInterval = 10.milliseconds

/** The content of the test file to be loaded from the cache. */
private const val TEST_CONTENT = """
    Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore
    et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum.
    Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet,
    consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed
    diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea
    takimata sanctus est Lorem ipsum dolor sit amet.
"""

/** Default path of the test file to be loaded from the cache. */
private const val TEST_PATH = "test/config/test.txt"

/**
 * Return a function that simulates loading a test file from GitHub. The function returns the test content with an
 * optional [prefix]. It checks that it is only invoked once.
 */
private fun loadFileFunc(prefix: String? = null): suspend () -> ByteReadChannel {
    val counter = AtomicInteger()

    return {
        require(counter.getAndIncrement() == 0) {
            "The load function must be called only once for '$prefix'."
        }

        ByteReadChannel(testFileContent(prefix))
    }
}

/**
 * Generate the content of a test file with an optional [prefix].
 */
private fun testFileContent(prefix: String? = null) = prefix.orEmpty() + TEST_CONTENT

/**
 * Verify that this [InputStream] contains the expected content with the given [prefix]. Also close the stream.
 */
private fun InputStream.verifyContent(prefix: String? = null) {
    use {
        String(readAllBytes()) shouldBe testFileContent(prefix)
    }
}

/**
 * Return a function that simulates obtaining the content of a test folder from GitHub. The folder content is generated
 * using [testFolderContent]. The function checks that it is only invoked once for a given set of parameters.
 */
private fun loadFolderContentFunc(name: String, revision: String): suspend () -> Set<String> {
    val counter = AtomicInteger()

    return {
        require(counter.getAndIncrement() == 0) {
            "The load function must be called only once for '$name' at revision '$revision'."
        }

        testFolderContent(name, revision)
    }
}

/**
 * Generate the content of a test folder for the given [name] and [revision].
 */
private fun testFolderContent(name: String, revision: String): Set<String> = setOf(
    "$name/file1-$revision.txt",
    "$name/file2-$revision.doc",
    "$name/file3-$revision.kt"
)

/**
 * Generate a revision based on the given [index].
 */
private fun revision(index: Int): String = "rev$index"

/**
 * Return a test instance of [GitHubConfigFileCache] backed by a managed temporary directory.
 */
private fun TestConfiguration.createCache(): GitHubConfigFileCache =
    GitHubConfigFileCache(tempdir(), lockCheckInterval)
