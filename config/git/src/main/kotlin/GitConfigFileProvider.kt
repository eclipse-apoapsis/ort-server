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

import com.typesafe.config.Config

import java.io.File
import java.io.InputStream

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigFileProvider
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path

import org.ossreviewtoolkit.downloader.WorkingTree
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.versioncontrolsystems.git.GitFactory
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

import org.slf4j.LoggerFactory

/**
 * An implementation of [ConfigFileProvider] that reads config files from Git and stores them to a local directory. The
 * directory is temporary and only exists for the lifetime of a job.
 */
class GitConfigFileProvider internal constructor(
    private val gitUrl: String,
    private val configDir: File
) : ConfigFileProvider {
    companion object {
        /**
         * Configuration property for the Git URL.
         */
        const val GIT_URL = "gitUrl"

        private val logger = LoggerFactory.getLogger(GitConfigFileProvider::class.java)

        /**
         * Create a new instance of [GitConfigFileProvider] that is initialized based on the given [config].
         */
        fun create(config: Config): GitConfigFileProvider {
            val gitUrl = config.getString(GIT_URL)

            logger.info("Creating GitConfigFileProvider for repository '{}'.", gitUrl)

            return GitConfigFileProvider(gitUrl, createOrtTempDir())
        }
    }

    private val git = GitFactory.create()
    private val lock = Any()

    private lateinit var workingTree: WorkingTree
    private lateinit var resolvedRevision: String

    override fun resolveContext(context: Context): Context {
        updateWorkingTree(context.name)
        return Context(resolvedRevision)
    }

    override fun getFile(context: Context, path: Path): InputStream =
        runCatching {
            updateWorkingTree(context.name)
            configDir.resolve(path.path).inputStream()
        }.getOrElse {
            throw ConfigException("Cannot read path '${path.path}'.", it)
        }

    override fun contains(context: Context, path: Path): Boolean {
        updateWorkingTree(context.name)
        return configDir.resolve(path.path).isFile
    }

    override fun listFiles(context: Context, path: Path): Set<Path> {
        updateWorkingTree(context.name)

        val dir = configDir.resolve(path.path)

        if (!dir.isDirectory) {
            throw ConfigException("The provided path '${path.path}' does not refer a directory.", null)
        }

        return dir.walk().maxDepth(1).filter { it.isFile }.mapTo(mutableSetOf()) { Path(it.path) }
    }

    /**
     * Initialize the working tree with the requested [revision]. If the revision is empty, the default branch is used.
     * The revision used for initialization is returned.
     */
    private fun initWorkingTree(revision: String): String {
        return synchronized(lock) {
            val initRevision = revision.takeUnless { it.isEmpty() } ?: git.getDefaultBranchName(gitUrl)
            val vcsInfo = VcsInfo(VcsType.GIT, gitUrl, initRevision)

            workingTree = git.initWorkingTree(configDir, vcsInfo)
            initRevision
        }
    }

    /**
     * Update the working tree to the [requestedRevision]. If the [configDir] does not contain a ".git" subdirectory,
     * the working tree is initialized first.
     */
    private fun updateWorkingTree(requestedRevision: String) {
        synchronized(lock) {
            // TODO: There might be a better way to do check if the configDir already contains a Git repository.
            val revision = if (!configDir.resolve(".git").isDirectory) {
                initWorkingTree(requestedRevision)
            } else {
                requestedRevision
            }

            // Check if the requested revision was already checked out.
            if (revision == workingTree.getRevision()) return

            // Update the working tree to the requested revision.
            git.updateWorkingTree(workingTree, revision, recursive = true)
            resolvedRevision = workingTree.getRevision()
        }
    }
}
