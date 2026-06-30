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
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldNotBeEmpty
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify

import java.io.IOException

import org.eclipse.apoapsis.ortserver.model.SubmoduleFetchStrategy

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.PluginConfig

class AnalyzerDownloaderTest : WordSpec({
    val downloader = AnalyzerDownloader()

    val repositoryUrl = "https://github.com/oss-review-toolkit/ort-test-data-scanner.git"
    val revision = "abc123def456"
    val resolvedRevision = "def456abc123def456abc123def456abc123def456"

    fun createMockVcs(mockWorkingTree: WorkingTree): VersionControlSystem =
        mockk<VersionControlSystem> {
            every { type } returns VcsType.GIT
            every { initWorkingTree(any(), any()) } returns mockWorkingTree
            every { updateWorkingTree(any(), any(), any(), any()) } returns Result.success(resolvedRevision)
            every { getWorkingTree(any()) } returns mockWorkingTree
        }

    fun createMockWorkingTree(): WorkingTree =
        mockk<WorkingTree> {
            every { getRevision() } returns resolvedRevision
        }

    "downloadRepository" should {
        "use the default branch if an empty revision is given" {
            val defaultBranch = "main"
            val mockWorkingTree = createMockWorkingTree()
            val mockVcs = createMockVcs(mockWorkingTree)
            every { mockVcs.getDefaultBranchName(repositoryUrl) } returns defaultBranch
            every {
                mockVcs.updateWorkingTree(any(), defaultBranch, any(), any())
            } returns Result.success(resolvedRevision)

            mockkObject(VersionControlSystem) {
                every { VersionControlSystem.forUrl(repositoryUrl, any()) } returns mockVcs

                val result = downloader.downloadRepository(repositoryUrl, revision = "")

                result.initRevision shouldBe defaultBranch
                result.resolvedRevision shouldBe resolvedRevision
            }
        }

        "return the initial and resolved revisions" {
            val mockWorkingTree = createMockWorkingTree()
            val mockVcs = createMockVcs(mockWorkingTree)

            mockkObject(VersionControlSystem) {
                every { VersionControlSystem.forUrl(repositoryUrl, any()) } returns mockVcs

                val result = downloader.downloadRepository(repositoryUrl, revision = revision)

                result.initRevision shouldBe revision
                result.resolvedRevision shouldBe resolvedRevision
            }
        }

        "not recursively clone a Git repository if submoduleFetchStrategy is DISABLED" {
            val mockWorkingTree = createMockWorkingTree()
            val mockVcs = createMockVcs(mockWorkingTree)

            mockkObject(VersionControlSystem) {
                every { VersionControlSystem.forUrl(repositoryUrl, any()) } returns mockVcs

                downloader.downloadRepository(
                    repositoryUrl,
                    revision,
                    submoduleFetchStrategy = SubmoduleFetchStrategy.DISABLED
                )

                verify(exactly = 1) { mockVcs.updateWorkingTree(any(), any(), any(), recursive = false) }
            }
        }

        "clone only the top level of a Git repository if submoduleFetchStrategy is TOP_LEVEL_ONLY" {
            val submodulesRepositoryUrl = "https://github.com/oss-review-toolkit/ort-test-data-git-submodules.git"
            val mockWorkingTree = createMockWorkingTree()
            val mockVcs = createMockVcs(mockWorkingTree)

            mockkObject(VersionControlSystem) {
                every { VersionControlSystem.forUrl(submodulesRepositoryUrl, any()) } returns mockVcs

                downloader.downloadRepository(
                    submodulesRepositoryUrl,
                    revision,
                    submoduleFetchStrategy = SubmoduleFetchStrategy.TOP_LEVEL_ONLY
                )

                verify(exactly = 1) {
                    VersionControlSystem.forUrl(
                        submodulesRepositoryUrl,
                        match { config ->
                            config[VcsType.GIT.toString()]?.options?.get("updateNestedSubmodules") == false.toString()
                        }
                    )
                }
                verify(exactly = 1) { mockVcs.updateWorkingTree(any(), any(), any(), recursive = true) }
            }
        }

        "fully recursively clone a Git repository if submoduleFetchStrategy is FULLY_RECURSIVE" {
            val submodulesRepositoryUrl = "https://github.com/oss-review-toolkit/ort-test-data-git-submodules.git"
            val mockWorkingTree = createMockWorkingTree()
            val mockVcs = createMockVcs(mockWorkingTree)

            mockkObject(VersionControlSystem) {
                every { VersionControlSystem.forUrl(submodulesRepositoryUrl, any()) } returns mockVcs

                downloader.downloadRepository(
                    submodulesRepositoryUrl,
                    revision,
                    submoduleFetchStrategy = SubmoduleFetchStrategy.FULLY_RECURSIVE
                )

                verify(exactly = 1) {
                    VersionControlSystem.forUrl(submodulesRepositoryUrl, emptyMap())
                }
                verify(exactly = 1) { mockVcs.updateWorkingTree(any(), any(), any(), recursive = true) }
            }
        }

        "clone a sub-directory of a Git repository" {
            val subPath = "pkg1"
            val mockWorkingTree = createMockWorkingTree()
            val mockVcs = createMockVcs(mockWorkingTree)

            mockkObject(VersionControlSystem) {
                every { VersionControlSystem.forUrl(repositoryUrl, any()) } returns mockVcs

                downloader.downloadRepository(repositoryUrl, revision, subPath)

                verify(exactly = 1) {
                    mockVcs.initWorkingTree(
                        any(),
                        match { vcsInfo -> vcsInfo.path == subPath }
                    )
                }
            }
        }

        "throw an exception if the VCS type cannot be determined from the URL" {
            mockkObject(VersionControlSystem) {
                every { VersionControlSystem.forUrl(any(), any()) } returns null

                shouldThrow<IllegalArgumentException> {
                    downloader.downloadRepository("https://example.com", "revision")
                }
            }
        }

        "throw an exception if the repository cannot be cloned" {
            val mockWorkingTree = createMockWorkingTree()
            val mockVcs = createMockVcs(mockWorkingTree)
            every {
                mockVcs.updateWorkingTree(any(), any(), any(), any())
            } returns Result.failure(IOException("Failed to update working tree to revision 'invalid-revision'."))

            mockkObject(VersionControlSystem) {
                every { VersionControlSystem.forUrl(repositoryUrl, any()) } returns mockVcs

                shouldThrow<IOException> {
                    downloader.downloadRepository(repositoryUrl, revision = "invalid-revision")
                }
            }
        }

        "allow customizing the download folder" {
            val targetDir = tempdir()
            val runId = 20260630054526L
            val mockWorkingTree = createMockWorkingTree()
            val mockVcs = createMockVcs(mockWorkingTree)

            mockkObject(VersionControlSystem) {
                every { VersionControlSystem.forUrl(repositoryUrl, any()) } returns mockVcs

                val result = downloader.downloadRepository(
                    repositoryUrl,
                    revision = revision,
                    targetDir = targetDir,
                    runId = runId
                )

                result.directory.isDirectory shouldBe true
                result.directory.parentFile shouldBe targetDir
                result.directory.name shouldContain runId.toString()
            }
        }
    }

    "buildCustomVcsPluginConfigurations" should {
        "return an empty map if the submoduleFetchStrategy is null" {
            val configurations = downloader.buildCustomVcsPluginConfigMap(repositoryUrl, null)

            configurations.shouldBeEmpty()
        }

        "return an empty map if the submoduleFetchStrategy is DISABLED" {
            val configurations = downloader.buildCustomVcsPluginConfigMap(
                repositoryUrl,
                SubmoduleFetchStrategy.DISABLED
            )

            configurations.shouldBeEmpty()
        }

        "return custom configuration for git if the submoduleFetchStrategy is TOP_LEVEL_ONLY" {
            val configurations = downloader.buildCustomVcsPluginConfigMap(
                repositoryUrl,
                SubmoduleFetchStrategy.TOP_LEVEL_ONLY
            )

            configurations.shouldNotBeEmpty()

            configurations shouldContainExactly mapOf(
                VcsType.GIT.toString() to PluginConfig(
                    mapOf("updateNestedSubmodules" to false.toString())
                )
            )
        }

        "return an empty map if the submoduleFetchStrategy is FULLY_RECURSIVE" {
            val configurations = downloader.buildCustomVcsPluginConfigMap(
                repositoryUrl,
                SubmoduleFetchStrategy.FULLY_RECURSIVE
            )

            configurations.shouldBeEmpty()
        }

        "throw an IllegalArgumentException if the submoduleFetchStrategy is TOP_LEVEL_ONLY " +
                "and the VCS type is not GIT" {
            shouldThrow<IllegalArgumentException> {
                downloader.buildCustomVcsPluginConfigMap("https://example.com", SubmoduleFetchStrategy.TOP_LEVEL_ONLY)
            }
        }
    }

    "findDownloadDir" should {
        "find an existing download directory" {
            val runId = 20260630061242L
            val root = tempdir()
            val downloadDir = root.resolve("Temp-analyzer-worker-$runId-download0123456789")
            downloadDir.mkdirs()
            val otherDirs = listOf("foo", "bar", "baz", "analyzer-worker-20260630061243-download")
            otherDirs.forEach { root.resolve(it).mkdirs() }

            val foundDir = AnalyzerDownloader.findDownloadDir(root, runId)

            foundDir shouldBe downloadDir
        }

        "return null if no directory for the given runId can be found" {
            val runId = 20260630065648L
            val root = tempdir()
            val dirs = listOf("foo", "bar", "baz", "analyzer-worker-20260630061242-download")
            dirs.forEach { root.resolve(it).mkdirs() }

            val foundDir = AnalyzerDownloader.findDownloadDir(root, runId)

            foundDir should beNull()
        }

        "return null if no unique directory for the given runId can be found" {
            val runId = 20260630065927L
            val root = tempdir()
            val dirs = listOf(
                "analyzer-worker-$runId-download-1",
                "analyzer-worker-$runId-download-2"
            )
            dirs.forEach { root.resolve(it).mkdirs() }

            val foundDir = AnalyzerDownloader.findDownloadDir(root, runId)

            foundDir should beNull()
        }

        "return null for a non-existing root directory" {
            AnalyzerDownloader.findDownloadDir(tempdir().resolve("non-existing"), 20260630065927L) should beNull()
        }
    }
})
