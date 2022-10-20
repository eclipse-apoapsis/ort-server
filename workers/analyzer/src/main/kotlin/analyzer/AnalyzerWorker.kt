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

import kotlinx.coroutines.delay

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.curation.OrtConfigPackageCurationProvider
import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.server.api.v1.AnalyzerJob
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AnalyzerJob::class.java)

internal class AnalyzerWorker(private val client: ServerClient) {
    private var started = true

    suspend fun start() {
        while (started) {
            logger.info("Waiting for Analyzer Job...")
            client.getScheduledAnalyzerJob()?.let { startedJob ->
                runCatching {
                    logger.info("Analyzer job with id ${startedJob.id} started at ${startedJob.startedAt}.")
                    logger.info("Running...")
                    val sourcesDir = startedJob.download()
                    startedJob.analyze(sourcesDir)
                    client.finishAnalyzerJob(startedJob.id)?.let { finishedJob ->
                        logger.info("Analyzer job with id ${finishedJob.id} finished at ${finishedJob.finishedAt}.")
                    }
                }.onFailure {
                    logger.error("Error during the analyzer job", it)
                    client.reportAnalyzerJobFailure(startedJob.id)
                }
            }
            delay(10 * 1000)
        }
    }

    fun stop() { started = false }

    /**
     * Download a repository for a given [AnalyzerJob]. Return the temporary directory containing the download.
     */
    private fun AnalyzerJob.download(): File {
        logger.info("Run analyzer job '$id'.")

        val repositoryName = repositoryUrl.substringAfterLast("/")
        val dummyId = Identifier("Downloader::$repositoryName:")
        val outputDir = createOrtTempDir("analyzer-worker")

        val vcs = VersionControlSystem.forUrl(repositoryUrl)
        val vcsType = vcs?.type ?: VcsType.UNKNOWN

        val vcsInfo = VcsInfo(
            type = vcsType,
            url = repositoryUrl,
            revision = repositoryRevision
        )

        logger.info("Downloading from $vcsType VCS at $repositoryUrl...")
        val dummyPackage = Package.EMPTY.copy(id = dummyId, vcs = vcsInfo, vcsProcessed = vcsInfo.normalize())

        // Always allow moving revisions when directly downloading a single project only. This is for
        // convenience as often the latest revision (referred to by some VCS-specific symbolic name) of a
        // project needs to be downloaded.
        val config = DownloaderConfiguration(allowMovingRevisions = true)
        val provenance = Downloader(config).download(dummyPackage, outputDir)
        logger.info("Successfully downloaded $provenance.")

        return outputDir
    }

    /**
     * Analyze a repository download in [inputDir] for a given [AnalyzerJob]. Return the file containing the result.
     */
    private fun AnalyzerJob.analyze(inputDir: File): OrtResult {
        val config = AnalyzerConfiguration(configuration.allowDynamicVersions)
        val analyzer = Analyzer(config)

        //   Add support for RepositoryConfiguration.
        val info = analyzer.findManagedFiles(inputDir, PackageManager.ALL, RepositoryConfiguration())
        if (info.managedFiles.isEmpty()) {
            logger.warn("No definition files found.")
        } else {
            val filesPerManager = info.managedFiles.mapKeysTo(sortedMapOf()) { it.key.managerName }
            var count = 0

            filesPerManager.forEach { (manager, files) ->
                count += files.size
                logger.info("Found ${files.size} $manager definition file(s) at:")

                files.forEach { file ->
                    val relativePath = file.toRelativeString(inputDir).takeIf { it.isNotEmpty() } ?: "."
                    logger.info("\t$relativePath")
                }
            }

            logger.info("Found $count definition file(s) from ${filesPerManager.size} package manager(s) in total.")
        }

        // TODO: Add support for the curation providers.
        val curationProvider = OrtConfigPackageCurationProvider()
        val ortResult = analyzer.analyze(info, curationProvider)

        val projectCount = ortResult.getProjects().size
        val packageCount = ortResult.getPackages().size
        logger.info(
            "Found $projectCount project(s) and $packageCount package(s) in total (not counting excluded ones)."
        )

        val curationCount = ortResult.getPackages().sumOf { it.curations.size }
        logger.info("Applied $curationCount curation(s) from 1 provider.")

        check(ortResult.analyzer?.result != null) {
            "There was an error creating the analyzer result."
        }

        // TODO: Store the Analyzer result in the database.
        return ortResult
    }
}
