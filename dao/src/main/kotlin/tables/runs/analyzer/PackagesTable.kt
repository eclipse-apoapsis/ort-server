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

package org.ossreviewtoolkit.server.dao.tables.runs.analyzer

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifierDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifiersTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.LicenseStringDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactsTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoTable
import org.ossreviewtoolkit.server.model.runs.Package

/**
 * A table to represent all metadata for a software package.
 */
object PackagesTable : LongIdTable("packages") {
    val identifierId = reference("identifier_id", IdentifiersTable.id, ReferenceOption.CASCADE)
    val vcsId = reference("vcs_id", VcsInfoTable.id, ReferenceOption.CASCADE)
    val vcsProcessedId = reference("vcs_processed_id", VcsInfoTable.id, ReferenceOption.CASCADE)
    val binaryArtifactId = reference("binary_artifact_id", RemoteArtifactsTable.id, ReferenceOption.CASCADE)
    val sourceArtifactId = reference("source_artifact_id", RemoteArtifactsTable.id, ReferenceOption.CASCADE)

    val purl = text("purl")
    val cpe = text("cpe").nullable()
    val description = text("description")
    val homepageUrl = text("homepage_url")
    val isMetadataOnly = bool("is_metadata_only").default(false)
    val isModified = bool("is_modified").default(false)
}

class PackageDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PackageDao>(PackagesTable)

    var identifier by IdentifierDao referencedOn PackagesTable.identifierId
    var vcs by VcsInfoDao referencedOn PackagesTable.vcsId
    var vcsProcessed by VcsInfoDao referencedOn PackagesTable.vcsProcessedId
    var authors by AuthorDao via PackagesAuthorsTable
    var binaryArtifact by RemoteArtifactDao referencedOn PackagesTable.binaryArtifactId
    var sourceArtifact by RemoteArtifactDao referencedOn PackagesTable.sourceArtifactId
    var declaredLicenses by LicenseStringDao via PackagesDeclaredLicensesTable
    var analyzerRuns by AnalyzerRunDao via PackagesAnalyzerRunsTable

    var purl by PackagesTable.purl
    var cpe by PackagesTable.cpe
    var description by PackagesTable.description
    var homepageUrl by PackagesTable.homepageUrl
    var isMetadataOnly by PackagesTable.isMetadataOnly
    var isModified by PackagesTable.isModified

    fun mapToModel() = Package(
        id.value,
        identifier.mapToModel(),
        purl,
        cpe,
        authors.map(AuthorDao::mapToModel).toSet(),
        declaredLicenses.map(LicenseStringDao::mapToModel).toSet(),
        description,
        homepageUrl,
        binaryArtifact.mapToModel(),
        sourceArtifact.mapToModel(),
        vcs.mapToModel(),
        vcsProcessed.mapToModel(),
        isMetadataOnly,
        isModified
    )
}
