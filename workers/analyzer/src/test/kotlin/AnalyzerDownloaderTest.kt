/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.analyzer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.file.beEmptyDirectory
import io.kotest.matchers.file.shouldContainFile
import io.kotest.matchers.file.shouldNotExist
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

import java.io.IOException

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class AnalyzerDownloaderTest : WordSpec({
    val downloader = AnalyzerDownloader()

    "downloadRepository" should {
        "not recursively clone a Git repository if recursiveCheckout is false" {
            val repositoryUrl = "https://github.com/oss-review-toolkit/ort-test-data-git-submodules.git"
            val revision = "fcea94bab5835172e826afddb9f6427274c983b9"

            val outputDir = downloader.downloadRepository(repositoryUrl, revision, recursiveCheckout = false)

            outputDir shouldContainFile "LICENSE"
            outputDir shouldContainFile "README.md"

            outputDir.resolve("commons-text") should beEmptyDirectory()
            outputDir.resolve("test-data-npm") should beEmptyDirectory()

            val workingTree = VersionControlSystem.forDirectory(outputDir)
            workingTree.shouldNotBeNull()
            workingTree.getNested().shouldBeEmpty()
        }

        "recursively clone a Git repository if recursiveCheckout is true" {
            val repositoryUrl = "https://github.com/oss-review-toolkit/ort-test-data-git-submodules.git"
            val revision = "fcea94bab5835172e826afddb9f6427274c983b9"

            val outputDir = downloader.downloadRepository(repositoryUrl, revision, recursiveCheckout = true)

            outputDir shouldContainFile "LICENSE"
            outputDir shouldContainFile "README.md"

            outputDir.resolve("commons-text") shouldNot beEmptyDirectory()
            outputDir.resolve("test-data-npm") shouldNot beEmptyDirectory()

            val workingTree = VersionControlSystem.forDirectory(outputDir)
            workingTree.shouldNotBeNull()
            workingTree.getNested() shouldContainExactly mapOf(
                "commons-text" to VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/apache/commons-text.git",
                    revision = "7643b12421100d29fd2b78053e77bcb04a251b2e"
                ),
                "test-data-npm" to VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/oss-review-toolkit/ort-test-data-npm.git",
                    revision = "ad0367b7b9920144a47b8d30cc0c84cea102b821"
                ),
                "test-data-npm/isarray" to VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/juliangruber/isarray.git",
                    revision = "63ea4ca0a0d6b0574d6a470ebd26880c3026db4a"
                ),
                "test-data-npm/long.js" to VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/dcodeIO/long.js.git",
                    revision = "941c5c62471168b5d18153755c2a7b38d2560e58"
                )
            )
        }

        "clone a sub-directory of a Git repository" {
            val repositoryUrl = "https://github.com/oss-review-toolkit/ort-test-data-scanner.git"
            val revision = "63b81fda7961c7426672469caaf4fb350a9d4ee0"
            val subPath = "pkg1"

            val outputDir = downloader.downloadRepository(repositoryUrl, revision, subPath)

            outputDir shouldContainFile "LICENSE"
            outputDir shouldContainFile "README.md"

            outputDir.resolve("pkg1") shouldNot beEmptyDirectory()
            outputDir.resolve("pkg2").shouldNotExist()
            outputDir.resolve("pkg3").shouldNotExist()
            outputDir.resolve("pkg4").shouldNotExist()
        }

        "throw an exception if the VCS type cannot be determined from the URL" {
            shouldThrow<IllegalArgumentException> {
                downloader.downloadRepository("https://example.com", "revision")
            }
        }

        "throw an exception if the repository cannot be cloned" {
            val repositoryUrl = "https://github.com/oss-review-toolkit/ort-test-data-git-submodules.git"
            val revision = "invalid-revision"

            shouldThrow<IOException> {
                downloader.downloadRepository(repositoryUrl, revision)
            }
        }
    }
})
