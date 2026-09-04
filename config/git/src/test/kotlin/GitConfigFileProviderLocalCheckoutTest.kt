/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.booleans.beFalse
import io.kotest.matchers.booleans.beTrue
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

import java.io.File

import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.config.ResolvedConfigContext
import org.eclipse.jgit.api.Git as JGit
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.WorkingTree

private const val FILE_NAME = "config.txt"
private const val FIRST_CONTENT = "first"
private const val SECOND_CONTENT = "second"
private val MISSING_REVISION = "0".repeat(40)

class GitConfigFileProviderLocalCheckoutTest : WordSpec({
    "checkoutRevisionLocally" should {
        "checkout commits from the local object database" {
            val repositoryDir = tempdir()
            val (firstRevision, secondRevision) = createRepository(repositoryDir)
            val provider = GitConfigFileProvider("https://example.org/repository.git", repositoryDir)

            provider.checkoutRevisionLocally(firstRevision) should beTrue()
            repositoryDir.resolve(FILE_NAME).readText() shouldBe FIRST_CONTENT

            provider.checkoutRevisionLocally(secondRevision) should beTrue()
            repositoryDir.resolve(FILE_NAME).readText() shouldBe SECOND_CONTENT
        }

        "reject revisions that are not full object IDs" {
            val repositoryDir = tempdir()
            val (_, currentRevision) = createRepository(repositoryDir)
            val provider = GitConfigFileProvider("https://example.org/repository.git", repositoryDir)

            provider.checkoutRevisionLocally("main") should beFalse()
            getHeadRevision(repositoryDir) shouldBe currentRevision
        }

        "return false if the commit is not available locally" {
            val repositoryDir = tempdir()
            val (_, currentRevision) = createRepository(repositoryDir)
            val provider = GitConfigFileProvider("https://example.org/repository.git", repositoryDir)

            provider.checkoutRevisionLocally(MISSING_REVISION) should beFalse()
            getHeadRevision(repositoryDir) shouldBe currentRevision
        }
    }

    "file operations" should {
        "alternate between local commits without updating from the remote" {
            val repositoryDir = tempdir()
            val (firstRevision, secondRevision) = createRepository(repositoryDir)
            val workingTree = createWorkingTree(repositoryDir)
            val vcs = createVersionControlSystem(repositoryDir, workingTree)
            val provider = GitConfigFileProvider("https://example.org/repository.git", repositoryDir, git = vcs)

            provider.getFile(ResolvedConfigContext(firstRevision), Path(FILE_NAME))
                .bufferedReader().use { it.readText() } shouldBe FIRST_CONTENT
            provider.getFile(ResolvedConfigContext(secondRevision), Path(FILE_NAME))
                .bufferedReader().use { it.readText() } shouldBe SECOND_CONTENT
            provider.getFile(ResolvedConfigContext(firstRevision), Path(FILE_NAME))
                .bufferedReader().use { it.readText() } shouldBe FIRST_CONTENT

            verify(exactly = 0) { vcs.updateWorkingTree(any(), any(), any(), any()) }
        }

        "fall back to an ORT update if a commit is not available locally" {
            val repositoryDir = tempdir()
            repositoryDir.resolve(FILE_NAME).writeText(FIRST_CONTENT)
            val workingTree = mockk<WorkingTree> {
                every { isValid() } returns true
                every { getRevision() } returnsMany listOf("current", MISSING_REVISION)
            }
            val vcs = createVersionControlSystem(repositoryDir, workingTree)
            every {
                vcs.updateWorkingTree(workingTree, MISSING_REVISION, recursive = false)
            } returns Result.success(MISSING_REVISION)
            val provider = GitConfigFileProvider("https://example.org/repository.git", repositoryDir, git = vcs)

            provider.contains(ResolvedConfigContext(MISSING_REVISION), Path(FILE_NAME)) shouldBe true

            verify(exactly = 1) {
                vcs.updateWorkingTree(workingTree, MISSING_REVISION, recursive = false)
            }
        }

        "fall back to an ORT update for a non-commit context" {
            val repositoryDir = tempdir()
            repositoryDir.resolve(FILE_NAME).writeText(FIRST_CONTENT)
            val workingTree = mockk<WorkingTree> {
                every { isValid() } returns true
                every { getRevision() } returnsMany listOf("current", "resolved-main")
            }
            val vcs = createVersionControlSystem(repositoryDir, workingTree)
            every {
                vcs.updateWorkingTree(workingTree, "main", recursive = false)
            } returns Result.success("main")
            val provider = GitConfigFileProvider("https://example.org/repository.git", repositoryDir, git = vcs)

            provider.contains(ResolvedConfigContext("main"), Path(FILE_NAME)) shouldBe true

            verify(exactly = 1) { vcs.updateWorkingTree(workingTree, "main", recursive = false) }
        }
    }
})

private fun createRepository(directory: File): Pair<String, String> =
    JGit.init().setDirectory(directory).call().use { git ->
        val firstRevision = git.commitFile(FIRST_CONTENT)
        val secondRevision = git.commitFile(SECOND_CONTENT)

        firstRevision to secondRevision
    }

private fun JGit.commitFile(content: String): String {
    repository.workTree.resolve(FILE_NAME).writeText(content)
    add().addFilepattern(FILE_NAME).call()

    val identity = PersonIdent("Test User", "test@example.org")
    return commit()
        .setMessage("Set test content")
        .setAuthor(identity)
        .setCommitter(identity)
        .call()
        .name
}

private fun createWorkingTree(repositoryDir: File): WorkingTree =
    mockk {
        every { isValid() } returns true
        every { getRevision() } answers { getHeadRevision(repositoryDir) }
    }

private fun createVersionControlSystem(repositoryDir: File, workingTree: WorkingTree): VersionControlSystem =
    mockk {
        every { getWorkingTree(repositoryDir) } returns workingTree
    }

private fun getHeadRevision(repositoryDir: File): String =
    JGit.open(repositoryDir).use { it.repository.resolve(Constants.HEAD).name }
