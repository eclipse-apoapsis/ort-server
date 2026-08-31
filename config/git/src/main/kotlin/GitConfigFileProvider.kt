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
import java.io.FilterInputStream
import java.io.InputStream
import java.util.HashMap

import kotlin.time.measureTime

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigFileProvider
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.config.resolveSecurely
import org.eclipse.apoapsis.ortserver.utils.config.getServiceUrl

import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.versioncontrolsystems.git.GitFactory
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

import org.slf4j.LoggerFactory

/**
 * An implementation of [ConfigFileProvider] that reads config files from Git and stores them to a local directory. The
 * directory is temporary and only exists for the lifetime of a job. The provider is thread-safe and all function calls
 * are processed sequentially to avoid race conditions when using different [Context]s.
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
            val gitUrl = config.getServiceUrl(GIT_URL)

            logger.info("Creating GitConfigFileProvider for repository '{}'.", gitUrl)

            return GitConfigFileProvider(gitUrl, createOrtTempDir())
        }
    }

    private val git = GitFactory.create(historyDepth = 1)

    /**
     * A lock guarding all access to the working tree in [configDir]. A single provider instance can be accessed
     * concurrently by multiple threads, and all of them share this instance's one working tree. Therefore, the sequence
     * of updating the tree to the requested revision and reading files from it must happen atomically. Otherwise, a
     * concurrent call requesting a different revision could re-checkout the tree between the update and the read,
     * causing files from the wrong revision, an inconsistent tree, or read failures to be observed.
     */
    private val lock = Any()

    /**
     * A dedicated temporary directory for storing snapshots of configuration files served via [getFile]. It exists for
     * the lifetime of this provider. Individual snapshot files in this directory are deleted when their stream is
     * closed.
     */
    internal val snapshotDir = createOrtTempDir()

    override fun resolveContext(context: Context): Context = synchronized(lock) {
        val resolvedRevision = updateWorkingTree(context.name)
        Context(resolvedRevision)
    }

    override fun getFile(context: Context, path: Path): InputStream =
        synchronized(lock) {
            runCatching {
                updateWorkingTree(context.name)

                // Copy the file to a temporary location while holding the lock, so that reading the returned stream is
                // not affected by a concurrent update of the working tree.
                val sourceFile = configDir.resolveSecurely(path)
                val tempFile = File.createTempFile("config", null, snapshotDir)

                runCatching {
                    sourceFile.inputStream().use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    DeleteOnCloseInputStream(tempFile)
                }.onFailure {
                    tempFile.delete()
                }.getOrThrow()
            }.getOrElse {
                throw ConfigException("Cannot read path '${path.path}'.", it)
            }
        }

    override fun contains(context: Context, path: Path): Boolean = synchronized(lock) {
        updateWorkingTree(context.name)
        val p = configDir.resolveSecurely(path)
        val isDirectoryPath = path.path.endsWith("/")

        (!isDirectoryPath && p.isFile) || (isDirectoryPath && p.isDirectory)
    }

    override fun listFiles(context: Context, path: Path): Set<Path> = synchronized(lock) {
        updateWorkingTree(context.name)

        val dir = configDir.resolveSecurely(path)

        if (!dir.isDirectory) {
            throw ConfigException("The provided path '${path.path}' does not refer a directory.")
        }

        dir.walk().maxDepth(1).filter { it.isFile }
            .mapTo(mutableSetOf()) { Path(it.relativeTo(configDir).path) }
    }

    /**
     * Update the working tree to the [requestedRevision]. If the [configDir] does not contain a ".git" subdirectory,
     * the working tree is initialized first. The resolved revision is returned.
     *
     * This function must not be called concurrently, all callers must make sure to synchronize against [lock].
     */
    private fun updateWorkingTree(requestedRevision: String): String {
        try {
            val revisionToCheckout = requestedRevision.ifBlank { git.getDefaultBranchName(gitUrl) }
            val workingTree = git.getWorkingTree(configDir)

            if (!workingTree.isValid()) {
                val vcsInfo = VcsInfo(VcsType.GIT, gitUrl, revisionToCheckout)

                measureTime { git.initWorkingTree(configDir, vcsInfo) }.also {
                    logger.debug("Initialized Git working tree in $it.")
                }
            }

            // Check if the requested revision was already checked out.
            if (workingTree.getRevision() == revisionToCheckout) return revisionToCheckout

            // Update the working tree to the requested revision.
            measureTime {
                git.updateWorkingTree(workingTree, revisionToCheckout, recursive = true).getOrThrow()
            }.also {
                logger.debug("Updated Git working tree to revision '$revisionToCheckout' in $it.")
            }

            return workingTree.getRevision()
        } finally {
            clearHttpAuthCache()
        }
    }

    /**
     * Clear the HTTP basic authentication cache used by HttpURLConnection.
     *
     * JGit uses HttpURLConnection for HTTP(S) connections, which caches authentication credentials.
     * If the Git repository requires authentication and the credentials change between requests,
     * the cached credentials may lead to authentication failures.
     *
     * Clearing the HTTP authentication cache ensures that new credentials are used for subsequent requests.
     *
     * Requires JVM argument: --add-opens java.base/sun.net.www.protocol.http=ALL-UNNAMED
     */
    private fun clearHttpAuthCache() = runCatching {
        logger.debug("Clearing JGit HTTP authentication cache.")

        val authCacheImplClass = Class.forName("sun.net.www.protocol.http.AuthCacheImpl")
        val getDefaultMethod = authCacheImplClass.getDeclaredMethod("getDefault")
        val defaultCache = getDefaultMethod.invoke(null)

        val setMapMethod = authCacheImplClass.getDeclaredMethod("setMap", HashMap::class.java)
        // Replace the cache map with an empty one.
        setMapMethod.invoke(defaultCache, HashMap<Any, Any>())

        logger.debug("Successfully cleared JGit HTTP authentication cache.")
    }.onFailure { e ->
        logger.warn(
            "Failed to clear JGit HTTP authentication cache. This may lead to Git authentication issues. Consider " +
                    "setting '--add-opens java.base/sun.net.www.protocol.http=ALL-UNNAMED' javaOpts.",
            e
        )
    }
}

/**
 * An [InputStream] that reads from the given [tempFile] and deletes it when the stream is closed. This is used to serve
 * the content of a configuration file from a temporary copy that is independent of the shared working tree.
 */
private class DeleteOnCloseInputStream(
    private val tempFile: File
) : FilterInputStream(tempFile.inputStream()) {
    override fun close() {
        try {
            super.close()
        } finally {
            tempFile.delete()
        }
    }
}
