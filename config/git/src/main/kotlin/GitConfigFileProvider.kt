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
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProvider
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path

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
    private val configDir: File,
    private val username: String? = null,
    private val token: String? = null
) : ConfigFileProvider {
    companion object {
        /**
         * Configuration property for the Git URL.
         */
        const val GIT_URL = "gitUrl"

        /** The path to the secret containing the Git username. */
        val USERNAME_SECRET = Path("gitConfigFileProviderUser")

        /** The path to the secret containing the Git token. */
        val TOKEN_SECRET = Path("gitConfigFileProviderToken")

        private val logger = LoggerFactory.getLogger(GitConfigFileProvider::class.java)

        /**
         * Create a new instance of [GitConfigFileProvider] that is initialized based on the given [config].
         */
        fun create(config: Config, secretProvider: ConfigSecretProvider): GitConfigFileProvider {
            val gitUrl = config.getString(GIT_URL)

            logger.info("Creating GitConfigFileProvider for repository '{}'.", gitUrl)

            val username = runCatching { secretProvider.getSecret(USERNAME_SECRET) }.onFailure {
                logger.info("Could not get $USERNAME_SECRET from secret provider, continuing without it.")
            }.getOrNull()

            val token = runCatching { secretProvider.getSecret(TOKEN_SECRET) }.onFailure {
                logger.info("Could not get $TOKEN_SECRET from secret provider, continuing without it.")
            }.getOrNull()

            return GitConfigFileProvider(gitUrl, createOrtTempDir(), username, token)
        }
    }

    private val git = GitFactory.create(historyDepth = 1)

    override fun resolveContext(context: Context): Context {
        val resolvedRevision = updateWorkingTree(context.name)
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
     * Update the working tree to the [requestedRevision]. If the [configDir] does not contain a ".git" subdirectory,
     * the working tree is initialized first. The resolved revision is returned.
     */
    private fun updateWorkingTree(requestedRevision: String): String {
        synchronized(this) {
            // TODO: There might be a better way to do check if the configDir already contains a Git repository.
            val revision = if (!configDir.resolve(".git").isDirectory) {
                withAuthenticator(username, token) {
                    val initRevision = requestedRevision.takeUnless { it.isEmpty() } ?: git.getDefaultBranchName(gitUrl)
                    val vcsInfo = VcsInfo(VcsType.GIT, gitUrl, initRevision)

                    git.initWorkingTree(configDir, vcsInfo)
                    initRevision
                }
            } else {
                requestedRevision
            }

            val workingTree = git.getWorkingTree(configDir)

            // Check if the requested revision was already checked out.
            if (revision == workingTree.getRevision()) return revision

            // Update the working tree to the requested revision.
            withAuthenticator(username, token) {
                git.updateWorkingTree(workingTree, revision, recursive = true)
            }

            return workingTree.getRevision()
        }
    }
}
