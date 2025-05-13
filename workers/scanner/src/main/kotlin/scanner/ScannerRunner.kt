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

package org.eclipse.apoapsis.ortserver.workers.scanner

import org.eclipse.apoapsis.ortserver.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.SubmoduleFetchStrategy
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtServerFileListStorage
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.downloader.DefaultWorkingTreeCache
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.SourceCodeOrigin
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.scanner.ScanStorages
import org.ossreviewtoolkit.scanner.Scanner
import org.ossreviewtoolkit.scanner.ScannerWrapper
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory
import org.ossreviewtoolkit.scanner.provenance.DefaultNestedProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.DefaultPackageProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.DefaultProvenanceDownloader

class ScannerRunner(
    private val db: Database,
    private val fileArchiver: FileArchiver,
    private val fileListStorage: OrtServerFileListStorage
) {
    companion object {
        /**
         * Convert the VCS plugin configurations to a canonical string representation. If there are no VCS plugin
         * configurations, return null.
         */
        fun createCanonicalVcsPluginConfigs(vcsPluginConfigs: Map<String, PluginConfig>) =
            vcsPluginConfigs.keys.sorted().joinToString(separator = "&") { vcs ->
                vcsPluginConfigs[vcs]?.options.orEmpty()
                    .toSortedMap().entries.joinToString(separator = "&") { (key, value) -> "$vcs/$key/$value" }
            }.ifEmpty { null }
    }

    suspend fun run(
        context: WorkerContext,
        ortResult: OrtResult,
        config: ScannerJobConfiguration,
        scannerRunId: Long
    ): OrtScannerResult {
        val pluginConfigs = context.resolvePluginConfigSecrets(config.config)

        // If the submodule fetch strategy is set to TOP_LEVEL_ONLY, for git use a plugin config that prevents that
        // submodules are fetched recursively.
        val vcsPluginConfigs = if (config.submoduleFetchStrategy == SubmoduleFetchStrategy.TOP_LEVEL_ONLY) {
            mapOf(
                VcsType.GIT.toString() to PluginConfig(
                    options = mapOf("updateNestedSubmodules" to "false")
                )
            )
        } else {
            emptyMap()
        }

        if (config.submoduleFetchStrategy == SubmoduleFetchStrategy.DISABLED) {
            throw ScannerException(
                "Scanner job configuration option SubmoduleFetchStrategy.DISABLED is not supported."
            )
        }

        val canonicalVcsPluginConfigs = createCanonicalVcsPluginConfigs(vcsPluginConfigs)
        val packageProvenanceCache = PackageProvenanceCache()
        val packageProvenanceStorage = OrtServerPackageProvenanceStorage(db, scannerRunId, packageProvenanceCache)
        val nestedProvenanceStorage = OrtServerNestedProvenanceStorage(
            db,
            packageProvenanceCache,
            canonicalVcsPluginConfigs
        )
        val scanResultStorage = OrtServerScanResultStorage(db, scannerRunId)

        val scanStorages = ScanStorages(
            readers = listOf(scanResultStorage),
            writers = listOf(scanResultStorage),
            packageProvenanceStorage = packageProvenanceStorage,
            nestedProvenanceStorage = nestedProvenanceStorage
        )

        val defaultScannerConfig = ScannerConfiguration()
        val scannerConfig = ScannerConfiguration(
            skipConcluded = config.skipConcluded ?: defaultScannerConfig.skipConcluded,
            detectedLicenseMapping = config.detectedLicenseMappings ?: defaultScannerConfig.detectedLicenseMapping,
            ignorePatterns = config.ignorePatterns.takeUnless { it.isNullOrEmpty() }
                ?: defaultScannerConfig.ignorePatterns,
            config = pluginConfigs.mapValues { it.value.mapToOrt() }.takeUnless { it.isEmpty() }
                ?: defaultScannerConfig.config
        )

        val downloaderConfig = DownloaderConfiguration(
            sourceCodeOrigins = config.sourceCodeOrigins?.distinct()?.map { it.mapToOrt() }
                ?: listOf(SourceCodeOrigin.ARTIFACT, SourceCodeOrigin.VCS)
        )

        val workingTreeCache = DefaultWorkingTreeCache().addVcsPluginConfigs(vcsPluginConfigs)

        val provenanceDownloader = DefaultProvenanceDownloader(downloaderConfig, workingTreeCache)
        val packageProvenanceResolver = DefaultPackageProvenanceResolver(
            scanStorages.packageProvenanceStorage,
            workingTreeCache
        )
        val nestedProvenanceResolver = DefaultNestedProvenanceResolver(
            scanStorages.nestedProvenanceStorage,
            workingTreeCache
        )

        val packageScannerWrappers = createScanners(config.scanners.orEmpty(), scannerConfig.config)
        val projectScannerWrappers =
            createScanners(config.projectScanners ?: config.scanners.orEmpty(), scannerConfig.config)

        try {
            val scanner = Scanner(
                scannerConfig = scannerConfig,
                downloaderConfig = downloaderConfig,
                provenanceDownloader = provenanceDownloader,
                storageReaders = scanStorages.readers,
                storageWriters = scanStorages.writers,
                packageProvenanceResolver = packageProvenanceResolver,
                nestedProvenanceResolver = nestedProvenanceResolver,
                scannerWrappers = mapOf(
                    PackageType.PACKAGE to packageScannerWrappers,
                    PackageType.PROJECT to projectScannerWrappers
                ),
                archiver = fileArchiver,
                fileListStorage = fileListStorage
            )

            val result = scanner.scan(ortResult, config.skipExcluded, emptyMap()).scanner
                ?: throw ScannerException("ORT Scanner failed to create a result.")
            return OrtScannerResult(result, scanResultStorage.getAllIssues())
        } finally {
            workingTreeCache.shutdown()
        }
    }
}

private fun createScanners(names: List<String>, config: Map<String, PluginConfig>?): List<ScannerWrapper> =
    names.map {
        ScannerWrapperFactory.ALL[it] ?: throw IllegalArgumentException(
            "Scanner '$it' is not one of ${ScannerWrapperFactory.ALL.keys.joinToString()}"
        )
    }.map {
        val pluginConfig = config?.get(it.descriptor.id)
        it.create(PluginConfig(pluginConfig?.options.orEmpty(), pluginConfig?.secrets.orEmpty()))
    }

/**
 * A data class used by [ScannerRunner] to return information about the result obtained from the invocation of the
 * ORT scanner.
 */
data class OrtScannerResult(
    /** The [ScannerRun] object produced by ORT's scanner. */
    val scannerRun: ScannerRun,

    /** A map with all issues found by the scanner, grouped by their provenance. */
    val issues: Map<Provenance, Set<Issue>>
)

internal class ScannerException(message: String) : Exception(message)
