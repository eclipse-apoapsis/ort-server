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

import java.io.File

import kotlin.io.path.createTempDirectory

import org.eclipse.apoapsis.ortserver.model.SubmoduleFetchStrategy

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.PluginConfig

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AnalyzerDownloader::class.java)

class AnalyzerDownloader {
    companion object {
        /**
         * Look up the directory of the download for the given [runId] under the given [root] directory based on the
         * naming conventions applied by this class. Return *null* if the directory cannot be determined or an error
         * occurs. This is useful if the download was done in a different phase of the analyzer run.
         */
        fun findDownloadDir(root: File, runId: Long): File? =
            runCatching {
                val dirName = downloadDirName(runId)

                root.listFiles { file -> file.isDirectory && dirName in file.name }.single()
            }.onFailure {
                logger.warn("Could not find download directory for runId '$runId' under root '$root'.", it)
            }.getOrNull()

        /**
         * Generate a name for a directory in which to download the repository for the run with the given [runId].
         * Based on this name, a temporary directory is created.
         */
        private fun downloadDirName(runId: Long?): String {
            val runComponent = runId?.let { "-$it" }.orEmpty()
            return "analyzer-worker$runComponent-download"
        }
    }

    /**
     * Download a VCS repository with the given [repositoryUrl], [revision], and optional [path] using ORT's Downloader
     * component. In case of a Git repository, apply the given [submoduleFetchStrategy]. Allow specifying the parent
     * [targetDir] for the download and the name of the download folder based on the provided [runId].
     */
    fun downloadRepository(
        repositoryUrl: String,
        revision: String,
        path: String = "",
        submoduleFetchStrategy: SubmoduleFetchStrategy? = SubmoduleFetchStrategy.FULLY_RECURSIVE,
        targetDir: File? = null,
        runId: Long? = null
    ): DownloadResult {
        logger.info("Downloading repository '$repositoryUrl' revision '$revision'.")

        val outputDir = createTempDirectory(targetDir?.toPath(), downloadDirName(runId)).toFile()

        val config = buildCustomVcsPluginConfigMap(repositoryUrl, submoduleFetchStrategy)
        val vcs = VersionControlSystem.forUrl(repositoryUrl, config)
        requireNotNull(vcs) { "Could not determine the VCS for URL '$repositoryUrl'." }

        val initRevision = revision.takeUnless { it.isEmpty() } ?: vcs.getDefaultBranchName(repositoryUrl)

        val vcsInfo = VcsInfo(
            type = vcs.type,
            url = repositoryUrl,
            revision = initRevision,
            path = path
        )

        val workingTree = vcs.initWorkingTree(outputDir, vcsInfo)
        val recursiveCheckout = submoduleFetchStrategy != SubmoduleFetchStrategy.DISABLED
        vcs.updateWorkingTree(workingTree, vcsInfo.revision, recursive = recursiveCheckout).getOrThrow()

        val resolvedRevision = vcs.getWorkingTree(outputDir).getRevision()

        logger.info(
            "Finished downloading '$repositoryUrl' revision '${vcsInfo.revision}' which was resolved to " +
                    "'$resolvedRevision'."
        )

        return DownloadResult(outputDir, initRevision, resolvedRevision)
    }

    /**
     * Build custom [PluginConfig] for Git VCS if the [submoduleFetchStrategy] is
     * [SubmoduleFetchStrategy.TOP_LEVEL_ONLY].
     */
    internal fun buildCustomVcsPluginConfigMap(
        repositoryUrl: String, submoduleFetchStrategy: SubmoduleFetchStrategy?
    ) =
        if (submoduleFetchStrategy == SubmoduleFetchStrategy.TOP_LEVEL_ONLY) {
            val vcsType = VcsHost.parseUrl(repositoryUrl).type
            require(vcsType == VcsType.GIT) {
                "Submodule fetch strategy TOP_LEVEL_ONLY is only supported for Git repositories, " +
                        "but got VCS type '$vcsType'."
            }
            mapOf(
                VcsType.GIT.toString() to PluginConfig(
                    options = mapOf("updateNestedSubmodules" to false.toString())
                )
            )
        } else {
            emptyMap()
        }
}

data class DownloadResult(
    /** The directory to which the repository was downloaded. */
    val directory: File,

    /** The revision used to initialize the repository. */
    val initRevision: String,

    /** The resolved revision of the repository. */
    val resolvedRevision: String
)
