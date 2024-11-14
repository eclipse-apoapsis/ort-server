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

package org.eclipse.apoapsis.ortserver.workers.reporter

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

import org.ossreviewtoolkit.downloader.Downloader
import org.ossreviewtoolkit.downloader.consolidateProjectPackagesByVcs
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.licenses.LicenseCategorization
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.encodeOrUnknown
import org.ossreviewtoolkit.utils.common.packZip
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.runBlocking
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseChoice

import org.slf4j.LoggerFactory

const val SOURCE_BUNDLE_FILE_NAME = "source-bundle-archive.zip"
const val SOURCE_BUNDLE_SUB_DIR = "source_code_bundle"

data class SourceCodeBundleReporterConfig(
    /**
     * The license categories to consider when including packages in the source code bundle.
     */
    val includedLicenseCategories: List<String>?,

    /**
     * The type of package to include in the source code bundle. Allowed values are "PROJECT" and "PACKAGE".
     */
    @OrtPluginOption(defaultValue = "PROJECT", aliases = ["packageType"])
    val packageTypes: List<String>
)

/**
 * A custom reporter that creates source code bundles.
 */
@OrtPlugin(
    id = "SourceCodeBundle",
    displayName = "Source Code Bundle Reporter",
    description = "A reporter that creates a source code bundle for the given ORT result.",
    factory = ReporterFactory::class
)
class SourceCodeBundleReporter(
    override val descriptor: PluginDescriptor = SourceCodeBundleReporterFactory.descriptor,
    private val config: SourceCodeBundleReporterConfig,
    private val downloader: Downloader
) : Reporter {
    constructor(
        descriptor: PluginDescriptor = SourceCodeBundleReporterFactory.descriptor,
        config: SourceCodeBundleReporterConfig
    ) : this(descriptor, config, Downloader(DownloaderConfiguration()))

    companion object {
        /**
         * Name of the property that specifies the type of package for source code bundle
         */
        const val PACKAGE_TYPE_PROPERTY = "packageType"

        /**
         * Name of the property that specifies the license classifications to include in the source code bundle.
         */
        const val INCLUDED_LICENSE_CATEGORIES_PROPERTY = "includedLicenseCategories"

        /** A file filter that simply includes all files. */
        private val includeAllFilter: (File) -> Boolean = { true }

        private val log = LoggerFactory.getLogger(SourceCodeBundleReporter::class.java)

        /**
         * Return a file filter for the source of a specific package based on the given [provenance] that has been
         * downloaded to the given [outputDir]. If the package has been downloaded from a repository and has the
         * `path` attribute set, only files below that path are included.
         */
        private fun fileFilterForProvenance(provenance: Provenance, outputDir: File): (File) -> Boolean =
            when (provenance) {
                is RepositoryProvenance ->
                    includeAllFilter.takeIf { provenance.vcsInfo.path.isEmpty() }
                        ?: createSubPathFilter(provenance.vcsInfo.path, outputDir)

                else -> includeAllFilter
            }

        /**
         * Return a filter that includes only files below the given [subPath] under the given [outputDir].
         */
        private fun createSubPathFilter(subPath: String, outputDir: File): (File) -> Boolean {
            val subPathDir = outputDir.resolve(subPath).toPath()

            return { file ->
                file.toPath().startsWith(subPathDir)
            }
        }
    }

    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        log.info("Preparing a source code bundle for repository '${input.ortResult.repository.vcsProcessed.url}'")

        val outputFile = runCatching {
            downloadSourceCode(
                input.ortResult,
                outputDir,
                input.licenseClassifications,
                config.includedLicenseCategories.orEmpty(),
                config.packageTypes
            )
        }

        return listOf(outputFile)
    }

    private fun downloadSourceCode(
        ortResult: OrtResult,
        outputDir: File,
        licenseClassifications: LicenseClassifications,
        includedLicenseCategories: List<String>,
        packageTypes: List<String>
    ): File {
        val allPackages = buildList {
            if (packageTypes.any { it.equals(PackageType.PROJECT.name, ignoreCase = true) }) {
                val projects = consolidateProjectPackagesByVcs(ortResult.getProjects(true)).keys
                log.info("Found ${projects.size} project(s) in the ORT result.")
                addAll(projects)
            }

            if (packageTypes.any { it.equals(PackageType.PACKAGE.name, ignoreCase = true) }) {
                val packages = ortResult.getPackages(true).map { it.metadata }
                log.info("Found ${packages.size} packages(s) in the ORT result.")
                addAll(packages)
            }
        }

        val licenseInfoResolver = ortResult.createLicenseInfoResolver()
        val filteredPackages = allPackages.takeIf { includedLicenseCategories.isEmpty() } ?: allPackages.filter { pkg ->
            val pkgLicenseCategories = pkg.getLicenseCategories(
                licenseClassifications.categorizations,
                licenseInfoResolver,
                ortResult.getPackageLicenseChoices(pkg.id)
            )

            pkgLicenseCategories.any { it in includedLicenseCategories }
        }

        val bundleDownloadDir = provideCodeBundleDownloadDir(outputDir)

        try {
            log.info("Downloading ${filteredPackages.size} project(s) / package(s) in total.")

            val packageDownloadDirs =
                filteredPackages.associateWith { bundleDownloadDir.resolve(it.id.toPath()) }

            runBlocking { downloadAllPackages(packageDownloadDirs, bundleDownloadDir) }

            val sourceCodeZipFile = outputDir.resolve(SOURCE_BUNDLE_FILE_NAME)

            log.info("Archiving directory '$bundleDownloadDir' to '$sourceCodeZipFile'.")
            bundleDownloadDir.packZip(sourceCodeZipFile)

            return sourceCodeZipFile
        } finally {
            bundleDownloadDir.safeDeleteRecursively(baseDirectory = bundleDownloadDir)
            bundleDownloadDir.delete()

            log.debug("Temp code bundle packages download dir ${bundleDownloadDir.absolutePath} deleted.")
        }
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

    /**
     * Download the source code of the given [pkg] to the given temporary [dir]. Then generate an archive with it in
     * the given [outputDir].
     */
    private fun downloadPackage(pkg: Package, dir: File, outputDir: File) {
        val provenance = downloader.download(pkg, dir)

        val filter = fileFilterForProvenance(provenance, dir)
        val zipFile = outputDir.resolve("${pkg.id.toPath("-")}.zip")

        log.info("Archiving directory '$dir' to '$zipFile'.")
        dir.packZip(
            zipFile,
            "${pkg.id.name.encodeOrUnknown()}/${pkg.id.version.encodeOrUnknown()}/",
            fileFilter = filter
        )
    }

    private fun provideCodeBundleDownloadDir(outputDir: File): File {
        require(outputDir.exists() && outputDir.isDirectory && outputDir.canWrite()) {
            "Output dir ${outputDir.absolutePath} doesn't exists or is not writeable."
        }

        val codeBundleDir = outputDir.resolve(SOURCE_BUNDLE_SUB_DIR)

        require(codeBundleDir.mkdir()) {
            "Can't create writable code bundle output dir ${codeBundleDir.absolutePath}."
        }

        log.debug("Temp dir ${codeBundleDir.absolutePath} for code bundle packages download created.")

        return codeBundleDir
    }

    /**
     * Retrieve the license categories for this [Package] based on its [effective license]
     * [ResolvedLicenseInfo.effectiveLicense].
     */
    private fun Package.getLicenseCategories(
        licenseCategorizations: List<LicenseCategorization>,
        licenseInfoResolver: LicenseInfoResolver,
        licenseChoices: List<SpdxLicenseChoice>
    ): Set<String> {
        val resolvedLicenseInfo = licenseInfoResolver.resolveLicenseInfo(id)
        val effectiveLicenses = resolvedLicenseInfo.effectiveLicense(
            LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED,
            licenseChoices
        )?.decompose().orEmpty()

        return licenseCategorizations
            .filter { it.id in effectiveLicenses }
            .flatMapTo(mutableSetOf()) { it.categories }
    }
}
