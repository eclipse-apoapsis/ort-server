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

package org.eclipse.apoapsis.ortserver.dao.tables

import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ArtifactProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.RepositoryProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScanResult
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerDetail
import org.eclipse.apoapsis.ortserver.model.runs.scanner.UnknownProvenance

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ISqlExpressionBuilder
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.and

/**
 * A table to represent a scan result.
 */
object ScanResultsTable : LongIdTable("scan_results") {
    val artifactUrl = text("artifact_url").nullable()
    val artifactHash = text("artifact_hash").nullable()
    val artifactHashAlgorithm = text("artifact_hash_algorithm").nullable()
    val vcsType = text("vcs_type").nullable()
    val vcsUrl = text("vcs_url").nullable()
    val vcsRevision = text("vcs_revision").nullable()
    val scanSummaryId = reference("scan_summary_id", ScanSummariesTable)
    val scannerName = text("scanner_name")
    val scannerVersion = text("scanner_version")
    val scannerConfiguration = text("scanner_configuration")
    val additionalScanResultData = jsonb<AdditionalScanResultData>("additional_data").nullable()
}

class ScanResultDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScanResultDao>(ScanResultsTable) {
        /**
         * Return an expression that encapsulates the condition that the scan result matches the given [artifact].
         */
        fun ISqlExpressionBuilder.matchesRemoteArtifact(artifact: RemoteArtifact): Op<Boolean> =
            (ScanResultsTable.artifactUrl eq artifact.url) and
                    (ScanResultsTable.artifactHash eq artifact.hashValue) and
                    (ScanResultsTable.artifactHashAlgorithm eq artifact.hashAlgorithm)

        /**
         * Return an expression that encapsulates the condition that the scan result matches the given [vcs].
         */
        fun ISqlExpressionBuilder.matchesVcsInfo(vcs: VcsInfo): Op<Boolean> =
            (ScanResultsTable.vcsType eq vcs.type.name) and
                    (ScanResultsTable.vcsUrl eq vcs.url) and
                    (ScanResultsTable.vcsRevision eq vcs.revision)
    }

    var artifactUrl by ScanResultsTable.artifactUrl
    var artifactHash by ScanResultsTable.artifactHash
    var artifactHashAlgorithm by ScanResultsTable.artifactHashAlgorithm
    var vcsType by ScanResultsTable.vcsType
    var vcsUrl by ScanResultsTable.vcsUrl
    var vcsRevision by ScanResultsTable.vcsRevision
    var scanSummary by ScanSummaryDao referencedOn ScanResultsTable.scanSummaryId
    var scannerName by ScanResultsTable.scannerName
    var scannerVersion by ScanResultsTable.scannerVersion
    var scannerConfiguration by ScanResultsTable.scannerConfiguration
    var additionalScanResultData by ScanResultsTable.additionalScanResultData

    var scannerRuns by ScannerRunDao via ScannerRunsScanResultsTable

    fun mapToModel(): ScanResult {
        val provenance = when {
            artifactUrl != null -> ArtifactProvenance(
                sourceArtifact = RemoteArtifact(
                    url = checkNotNull(artifactUrl),
                    hashValue = checkNotNull(artifactHash),
                    hashAlgorithm = checkNotNull(artifactHashAlgorithm)
                )
            )

            vcsUrl != null -> RepositoryProvenance(
                vcsInfo = VcsInfo(
                    type = RepositoryType.forName(checkNotNull(vcsType)),
                    url = checkNotNull(vcsUrl),
                    revision = checkNotNull(vcsRevision),
                    path = ""
                ),
                resolvedRevision = checkNotNull(vcsRevision)
            )

            else -> UnknownProvenance
        }

        return ScanResult(
            provenance = provenance,
            scanner = ScannerDetail(
                name = scannerName,
                version = scannerVersion,
                configuration = scannerConfiguration
            ),
            summary = scanSummary.mapToModel(),
            additionalData = additionalScanResultData?.data.orEmpty()
        )
    }
}

@Serializable
data class AdditionalScanResultData(
    var data: Map<String, String>
)
