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

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AnalyzerDownloader::class.java)

class AnalyzerDownloader {
    fun downloadRepository(
        repositoryUrl: String,
        revision: String,
        path: String = "",
        recursiveCheckout: Boolean = true
    ): File {
        logger.info("Downloading repository '$repositoryUrl' revision '$revision'.")

        val outputDir = createOrtTempDir("analyzer-worker")

        val vcs = VersionControlSystem.forUrl(repositoryUrl)
        requireNotNull(vcs) { "Could not determine the VCS for URL '$repositoryUrl'." }

        val vcsInfo = VcsInfo(vcs.type, repositoryUrl, revision, path)

        val workingTree = vcs.initWorkingTree(outputDir, vcsInfo)
        vcs.updateWorkingTree(workingTree, revision, recursive = recursiveCheckout).getOrThrow()

        logger.info("Finished downloading '$repositoryUrl' revision '$revision'.")

        return outputDir
    }
}
