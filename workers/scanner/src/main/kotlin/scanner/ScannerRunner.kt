/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.scanner

import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.config.DownloaderConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.ScanStorages
import org.ossreviewtoolkit.scanner.Scanner
import org.ossreviewtoolkit.scanner.ScannerWrapper
import org.ossreviewtoolkit.scanner.provenance.DefaultNestedProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.DefaultPackageProvenanceResolver
import org.ossreviewtoolkit.scanner.provenance.DefaultProvenanceDownloader
import org.ossreviewtoolkit.scanner.utils.DefaultWorkingTreeCache
import org.ossreviewtoolkit.server.model.ScannerJobConfiguration

class ScannerRunner(
    private val packageProvenanceStorage: OrtServerPackageProvenanceStorage,
    private val nestedProvenanceStorage: OrtServerNestedProvenanceStorage,
    private val scanResultStorage: OrtServerScanResultStorage
) {
    fun run(ortResult: OrtResult, config: ScannerJobConfiguration): OrtResult {
        val scanStorages = ScanStorages(
            readers = listOf(scanResultStorage),
            writers = listOf(scanResultStorage),
            packageProvenanceStorage = packageProvenanceStorage,
            nestedProvenanceStorage = nestedProvenanceStorage
        )

        val scannerConfig = ScannerConfiguration()
        val downloaderConfig = DownloaderConfiguration()
        val workingTreeCache = DefaultWorkingTreeCache()
        val provenanceDownloader = DefaultProvenanceDownloader(downloaderConfig, workingTreeCache)
        val packageProvenanceResolver = DefaultPackageProvenanceResolver(
            scanStorages.packageProvenanceStorage,
            workingTreeCache
        )
        val nestedProvenanceResolver = DefaultNestedProvenanceResolver(
            scanStorages.nestedProvenanceStorage,
            workingTreeCache
        )
        val scannerWrappers = ScannerWrapper.ALL["ScanCode"]
            ?.let { listOf(it.create(scannerConfig, downloaderConfig)) }
            .orEmpty()

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
                    PackageType.PACKAGE to scannerWrappers,
                    PackageType.PROJECT to scannerWrappers
                )
            )

            return runBlocking { scanner.scan(ortResult, config.skipExcluded, emptyMap()) }
        } finally {
            runBlocking { workingTreeCache.shutdown() }
        }
    }
}
