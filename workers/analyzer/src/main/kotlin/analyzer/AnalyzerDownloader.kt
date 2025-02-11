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

import org.eclipse.apoapsis.ortserver.model.SubmoduleFetchStrategy

import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AnalyzerDownloader::class.java)

class AnalyzerDownloader {
    fun downloadRepository(
        repositoryUrl: String,
        revision: String,
        path: String = "",
        submoduleFetchStrategy: SubmoduleFetchStrategy? = SubmoduleFetchStrategy.FULLY_RECURSIVE
    ): File {
        logger.info("Downloading repository '$repositoryUrl' revision '$revision'.")

        val outputDir = createOrtTempDir("analyzer-worker")

        val config = buildCustomVcsPluginConfigMap(repositoryUrl, submoduleFetchStrategy)
        val vcs = VersionControlSystem.forUrl(repositoryUrl, config)
        requireNotNull(vcs) { "Could not determine the VCS for URL '$repositoryUrl'." }

        val vcsInfo = VcsInfo(
            type = vcs.type,
            url = repositoryUrl,
            revision = revision,
            path = path
        )

        val workingTree = vcs.initWorkingTree(outputDir, vcsInfo)
        val recursiveCheckout = submoduleFetchStrategy != SubmoduleFetchStrategy.DISABLED
        vcs.updateWorkingTree(workingTree, revision, recursive = recursiveCheckout).getOrThrow()

        logger.info("Finished downloading '$repositoryUrl' revision '$revision'.")

        return outputDir
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
