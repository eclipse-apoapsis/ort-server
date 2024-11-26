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

import kotlinx.datetime.toKotlinInstant

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.tables.AdditionalScanResultData
import org.eclipse.apoapsis.ortserver.dao.tables.CopyrightFindingDao
import org.eclipse.apoapsis.ortserver.dao.tables.LicenseFindingDao
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultDao
import org.eclipse.apoapsis.ortserver.dao.tables.ScanSummariesIssuesDao
import org.eclipse.apoapsis.ortserver.dao.tables.ScanSummaryDao
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetDao
import org.eclipse.apoapsis.ortserver.dao.tables.SnippetFindingDao
import org.eclipse.apoapsis.ortserver.workers.common.mapToModel
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SizedCollection

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.scanner.ProvenanceBasedScanStorage
import org.ossreviewtoolkit.scanner.ScanStorageException
import org.ossreviewtoolkit.scanner.ScannerMatcher

/**
 * A class providing access to scan results.
 *
 * [ScanResult] are searched by details of a [KnownProvenance] which it is associated with. If the [KnownProvenance] is
 * an [ArtifactProvenance], the URL and the hash value must match. If the [KnownProvenance] is a [RepositoryProvenance],
 * the VCS type, URL and the resolved revision must match.
 *
 * Read and written scan results are associated to the scanner run with the provided [scannerRunId].
 *
 * Throws a [ScanStorageException] if an error occurs while reading from the storage.
 */
class OrtServerScanResultStorage(
    private val db: Database,
    private val scannerRunId: Long
) : ProvenanceBasedScanStorage {
    override fun read(provenance: KnownProvenance, scannerMatcher: ScannerMatcher?): List<ScanResult> =
        db.blockingQuery {
            val scanResultDaos = when (provenance) {
                is ArtifactProvenance -> {
                    ScanResultDao.findByRemoteArtifact(provenance.sourceArtifact.mapToModel())
                }

                is RepositoryProvenance -> {
                    ScanResultDao.findByVcsInfo(
                        provenance.vcsInfo.copy(revision = provenance.resolvedRevision).mapToModel()
                    )
                }
            }

            val matchingScanResults = scanResultDaos.associateWith {
                ScanResult(
                    provenance = provenance,
                    scanner = ScannerDetails(
                        name = it.scannerName,
                        version = it.scannerVersion,
                        configuration = it.scannerConfiguration
                    ),
                    summary = it.scanSummary.mapToModel().mapToOrt(),
                    additionalData = it.additionalScanResultData?.data.orEmpty()
                )
            }.filterValues { scannerMatcher?.matches(it.scanner) != false }

            matchingScanResults.forEach { (dao, _) ->
                associateScanResultWithScannerRun(dao)
            }

            matchingScanResults.values.toList()
        }

    override fun write(scanResult: ScanResult) {
        val provenance = scanResult.provenance

        if (provenance !is KnownProvenance) {
            throw ScanStorageException("Scan result must have a known provenance, but it is $provenance.")
        }

        if (provenance is RepositoryProvenance && provenance.vcsInfo.path.isNotEmpty()) {
            throw ScanStorageException("Repository provenances with a non-empty VCS path are not supported.")
        }

        db.blockingQuery {
            val summaryDao = ScanSummaryDao.new {
                this.startTime = scanResult.summary.startTime.toKotlinInstant()
                this.endTime = scanResult.summary.endTime.toKotlinInstant()
            }

            scanResult.summary.issues.forEach {
                ScanSummariesIssuesDao.createByIssue(summaryDao.id.value, it.mapToModel())
            }

            ScanResultDao.new {
                when (provenance) {
                    is ArtifactProvenance -> {
                        this.artifactUrl = provenance.sourceArtifact.url
                        this.artifactHash = provenance.sourceArtifact.hash.value
                        this.artifactHashAlgorithm = provenance.sourceArtifact.hash.algorithm.toString()
                    }

                    is RepositoryProvenance -> {
                        this.vcsType = provenance.vcsInfo.type.mapToModel().name
                        this.vcsUrl = provenance.vcsInfo.url
                        this.vcsRevision = provenance.resolvedRevision
                    }
                }

                this.scannerName = scanResult.scanner.name
                this.scannerVersion = scanResult.scanner.version
                this.scannerConfiguration = scanResult.scanner.configuration
                this.additionalScanResultData = AdditionalScanResultData(scanResult.additionalData)
                this.scanSummary = summaryDao

                val summary = this.scanSummary
                scanResult.summary.licenseFindings.forEach {
                    LicenseFindingDao.new {
                        this.scanSummary = summary
                        this.license = it.license.toString()
                        this.path = it.location.path
                        this.startLine = it.location.startLine
                        this.endLine = it.location.endLine
                        this.score = it.score
                    }
                }
                scanResult.summary.copyrightFindings.forEach {
                    CopyrightFindingDao.new {
                        this.scanSummary = summary
                        this.statement = it.statement
                        this.path = it.location.path
                        this.startLine = it.location.startLine
                        this.endLine = it.location.endLine
                    }
                }
                scanResult.summary.snippetFindings.forEach { snippetFinding ->
                    SnippetFindingDao.new {
                        this.scanSummary = summary
                        this.path = snippetFinding.sourceLocation.path
                        this.startLine = snippetFinding.sourceLocation.startLine
                        this.endLine = snippetFinding.sourceLocation.endLine
                        this.snippets = SizedCollection(
                            snippetFinding.snippets.map { snippet ->
                                SnippetDao.put(snippet.mapToModel())
                            }
                        )
                    }
                }
            }.also {
                associateScanResultWithScannerRun(it)
            }
        }
    }

    private fun associateScanResultWithScannerRun(scanResultDao: ScanResultDao) {
        ScannerRunsScanResultsTable.insertIfNotExists(
            scannerRunId = scannerRunId,
            scanResultId = scanResultDao.id.value
        )
    }
}
