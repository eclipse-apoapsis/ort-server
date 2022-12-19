/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.analyzer

import java.io.File

import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AnalyzerDownloader::class.java)

class AnalyzerDownloader {
    fun downloadRepository(repositoryUrl: String, revision: String): File {
        logger.info("Downloading repository '$repositoryUrl' revision '$revision'.")

        val repositoryName = repositoryUrl.substringAfterLast("/")
        val dummyId = Identifier("Downloader::$repositoryName:")
        val outputDir = createOrtTempDir("analyzer-worker")

        val vcs = VersionControlSystem.forUrl(repositoryUrl)

        val vcsType = vcs?.type ?: VcsType.UNKNOWN

        val vcsInfo = VcsInfo(
            type = vcsType,
            url = repositoryUrl,
            revision = revision
        )

        val dummyPackage = Package.EMPTY.copy(id = dummyId, vcs = vcsInfo, vcsProcessed = vcsInfo.normalize())

        // Always allow moving revisions when directly downloading a single project only. This is for
        // convenience as often the latest revision (referred to by some VCS-specific symbolic name) of a
        // project needs to be downloaded.
        val config = DownloaderConfiguration(allowMovingRevisions = true)
        val provenance = Downloader(config).download(dummyPackage, outputDir)
        logger.info("Successfully downloaded $provenance.")

        return outputDir
    }
}
