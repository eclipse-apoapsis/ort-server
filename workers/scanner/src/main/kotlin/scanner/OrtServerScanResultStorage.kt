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

import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.scanner.ProvenanceBasedScanStorage
import org.ossreviewtoolkit.scanner.ScanStorageException
import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.tables.AdditionalScanResultData
import org.ossreviewtoolkit.server.dao.tables.CopyrightFindingDao
import org.ossreviewtoolkit.server.dao.tables.LicenseFindingDao
import org.ossreviewtoolkit.server.dao.tables.ScanResultDao
import org.ossreviewtoolkit.server.dao.tables.ScanResultsTable
import org.ossreviewtoolkit.server.dao.tables.ScanSummaryDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.OrtIssueDao
import org.ossreviewtoolkit.server.model.runs.OrtIssue

/**
 * A class providing access to scan results.
 *
 * [ScanResult] are searched by details of a [KnownProvenance] which it is associated with. If the [KnownProvenance] is
 * an [ArtifactProvenance], the URL and the hash value must match. If the [KnownProvenance] is a [RepositoryProvenance],
 * the VCS type, URL and the resolved revision must match.
 *
 * Throws a [ScanStorageException] if an error occurs while reading from the storage.
 */
class OrtServerScanResultStorage(private val db: Database) : ProvenanceBasedScanStorage {
    override fun read(provenance: KnownProvenance): List<ScanResult> = db.blockingQuery {
            when (provenance) {
                is ArtifactProvenance -> {
                    ScanResultDao.find(
                        ScanResultsTable.artifactUrl eq provenance.sourceArtifact.url and
                                (ScanResultsTable.artifactHash eq provenance.sourceArtifact.hash.value) and
                                (
                                        ScanResultsTable.artifactHashAlgorithm eq
                                                provenance.sourceArtifact.hash.algorithm.toString()
                                        )
                    )
                }

                is RepositoryProvenance -> {
                    ScanResultDao.find(
                        ScanResultsTable.vcsType eq provenance.vcsInfo.type.toString() and
                                (ScanResultsTable.vcsUrl eq provenance.vcsInfo.url) and
                                (ScanResultsTable.vcsRevision eq provenance.resolvedRevision)
                    )
                }
            }.map {
                ScanResult(
                    provenance = provenance,
                    scanner = ScannerDetails(
                        name = it.scannerName,
                        version = it.scannerVersion,
                        configuration = it.scannerConfiguration
                    ),
                    summary = it.scanSummary.mapToOrt(),
                    additionalData = it.additionalScanResultData?.data ?: emptyMap()
                )
            }
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
            ScanResultDao.new {
                when (provenance) {
                    is ArtifactProvenance -> {
                        this.artifactUrl = provenance.sourceArtifact.url
                        this.artifactHash = provenance.sourceArtifact.hash.value
                        this.artifactHashAlgorithm = provenance.sourceArtifact.hash.algorithm.toString()
                    }

                    is RepositoryProvenance -> {
                        this.vcsType = provenance.vcsInfo.type.toString()
                        this.vcsUrl = provenance.vcsInfo.url
                        this.vcsRevision = provenance.resolvedRevision
                    }
                }

                val issues = scanResult.summary.issues.map(Issue::mapToModel).map(OrtIssueDao::getOrPut)

                this.scannerName = scanResult.scanner.name
                this.scannerVersion = scanResult.scanner.version
                this.scannerConfiguration = scanResult.scanner.configuration
                this.additionalScanResultData = AdditionalScanResultData(scanResult.additionalData)
                this.scanSummary = ScanSummaryDao.new {
                    this.startTime = scanResult.summary.startTime.toKotlinInstant()
                    this.endTime = scanResult.summary.endTime.toKotlinInstant()
                    this.issues = SizedCollection(issues)
                }

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
            }
        }
    }
}

private fun ScanSummaryDao.mapToOrt() = ScanSummary(
    this.startTime.toJavaInstant(),
    this.endTime.toJavaInstant(),
    this.licenseFindings.mapTo(mutableSetOf()) { it.mapToOrt() },
    this.copyrightFindings.mapTo(mutableSetOf()) { it.mapToOrt() },
    emptySet(), // TODO: Add snippet findings once implemented.
    this.issues.map {
        Issue(
            it.timestamp.toJavaInstant(),
            it.source,
            it.message,
            Severity.valueOf(it.severity)
        )
    }
)

private fun LicenseFindingDao.mapToOrt() = LicenseFinding(
    this.license,
    TextLocation(this.path, this.startLine, this.endLine),
    this.score
)

private fun CopyrightFindingDao.mapToOrt() = CopyrightFinding(
    this.statement,
    TextLocation(this.path, this.startLine, this.endLine)
)

private fun Issue.mapToModel() = OrtIssue(
    timestamp = this.timestamp.toKotlinInstant(),
    source = this.source,
    message = this.message,
    severity = this.severity.name
)
