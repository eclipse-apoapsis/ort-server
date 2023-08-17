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

package org.ossreviewtoolkit.server.dao.tables

import kotlinx.serialization.Serializable

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

import org.ossreviewtoolkit.server.dao.utils.jsonb
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.runs.RemoteArtifact
import org.ossreviewtoolkit.server.model.runs.VcsInfo
import org.ossreviewtoolkit.server.model.runs.scanner.ArtifactProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.RepositoryProvenance
import org.ossreviewtoolkit.server.model.runs.scanner.ScanResult
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerDetail
import org.ossreviewtoolkit.server.model.runs.scanner.UnknownProvenance

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
        fun findByRemoteArtifact(artifact: RemoteArtifact) =
            find {
                ScanResultsTable.artifactUrl eq artifact.url and
                        (ScanResultsTable.artifactHash eq artifact.hashValue) and
                        (ScanResultsTable.artifactHashAlgorithm eq artifact.hashAlgorithm)
            }

        fun findByVcsInfo(vcs: VcsInfo) =
            find {
                ScanResultsTable.vcsType eq vcs.type.name and
                        (ScanResultsTable.vcsUrl eq vcs.url) and
                        (ScanResultsTable.vcsRevision eq vcs.revision)
            }
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

    fun mapToModel(): ScanResult {
        val provenance = when {
            artifactUrl != null -> ArtifactProvenance(
                sourceArtifact = RemoteArtifact(
                    url = artifactUrl!!,
                    hashValue = artifactHash.orEmpty(),
                    hashAlgorithm = artifactHashAlgorithm.orEmpty()
                )
            )

            vcsUrl != null -> RepositoryProvenance(
                vcsInfo = VcsInfo(
                    type = RepositoryType.valueOf(vcsType!!),
                    url = vcsUrl!!,
                    revision = vcsRevision!!,
                    path = ""
                ),
                resolvedRevision = vcsRevision!!
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
