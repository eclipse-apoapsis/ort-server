/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.reporter

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.downloader.consolidateProjectPackagesByVcs
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.encodeOrUnknown
import org.ossreviewtoolkit.utils.common.packZip
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively

import org.slf4j.LoggerFactory

const val SOURCE_BUNDLE_FILE_NAME = "source-bundle-archive.zip"

/**
 * A custom reporter that creates source code bundles.
 */
class SourceCodeBundleReporter(
    private val downloader: Downloader = Downloader(DownloaderConfiguration())
) : Reporter {
    companion object {
        /**
         * Name of the property that specifies the type of package for source code bundle
         */
        const val PACKAGE_TYPE_PROPERTY = "packageType"

        /**
         * Name of the property that specifies the type of package for source code bundle
         */
        const val REPORTER_NAME = "SourceCodeBundle"

        private val log = LoggerFactory.getLogger(SourceCodeBundleReporter::class.java)
    }

    override val type = REPORTER_NAME

    override fun generateReport(input: ReporterInput, outputDir: File, config: PluginConfiguration): List<File> {
        log.info("Preparing a source code bundle for repository '${input.ortResult.repository.vcsProcessed.url}'")

        val outputFile = downloadSourceCode(
            input.ortResult,
            outputDir,
            config.options.getOrDefault(PACKAGE_TYPE_PROPERTY, "PROJECT")
        )

        return listOf(outputFile)
    }

    private fun downloadSourceCode(ortResult: OrtResult, outputDir: File, packageType: String): File {
        val packages = buildList {
            if (packageType.contains(PackageType.PROJECT.name, true)) {
                val projects = consolidateProjectPackagesByVcs(ortResult.getProjects(true)).keys
                log.info("Found ${projects.size} project(s) in the ORT result.")
                addAll(projects)
            }

            if (packageType.contains(PackageType.PACKAGE.name, true)) {
                val packages = ortResult.getPackages(true).map { it.metadata }
                log.info("Found ${packages.size} packages(s) in the ORT result.")
                addAll(packages)
            }
        }

        log.info("Downloading ${packages.size} project(s) / package(s) in total.")

        val packageDownloadDirs = packages.associateWith { outputDir.resolve(it.id.toPath()) }

        runBlocking { downloadAllPackages(packageDownloadDirs, outputDir) }

        val sourceCodeZipFile = outputDir.resolve(SOURCE_BUNDLE_FILE_NAME)

        log.info("Archiving directory '$outputDir' to '$sourceCodeZipFile'.")
        outputDir.packZip(sourceCodeZipFile)
        return sourceCodeZipFile
    }

    private suspend fun downloadAllPackages(packageDownloadDirs: Map<Package, File>, outputDir: File) {
        withContext(Dispatchers.IO) {
            packageDownloadDirs.entries.mapIndexed { index, (pkg, dir) ->
                async {
                    val progress = "${index + 1} of ${packageDownloadDirs.size}"

                    log.info("Starting download for '${pkg.id.toCoordinates()}' ($progress).")

                    try {
                        downloadPackage(pkg, dir, outputDir).also {
                            log.info("Finished download for ${pkg.id.toCoordinates()} ($progress).")
                        }
                    } finally {
                        dir.safeDeleteRecursively(baseDirectory = outputDir)
                    }
                }
            }.awaitAll()
        }
    }

    private fun downloadPackage(pkg: Package, dir: File, outputDir: File) {
        downloader.download(pkg, dir)

        val zipFile = outputDir.resolve("${pkg.id.toPath("-")}.zip")

        log.info("Archiving directory '$dir' to '$zipFile'.")
        dir.packZip(zipFile, "${pkg.id.name.encodeOrUnknown()}/${pkg.id.version.encodeOrUnknown()}/")
    }
}
