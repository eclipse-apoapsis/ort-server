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

import org.ossreviewtoolkit.server.dao.tables.runs.shared.LicenseSpdxDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.LicenseSpdxTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactsTable
import org.ossreviewtoolkit.server.model.runs.PackageCurationData

/**
 * A table to represent a package curation.
 */
object PackageCurationDataTable : LongIdTable("package_curation_data") {
    val binaryArtifactId = reference("binary_artifact_id", RemoteArtifactsTable.id).nullable()
    val sourceArtifactId = reference("source_artifact_id", RemoteArtifactsTable.id).nullable()
    val vcsId = reference("vcs_id", CurationVcsInfoTable.id).nullable()
    val concludedLicenseSpdxId = reference("concluded_license_spdx_id", LicenseSpdxTable.id).nullable()

    val purl = text("purl").nullable()
    val cpe = text("cpe").nullable()
    val comment = text("comment").nullable()
    val description = text("description").nullable()
    val homepageUrl = text("homepage_url").nullable()
    val isModified = bool("is_modified").nullable()
    val isMetadataOnly = bool("is_metadata_only").nullable()
}

class PackageCurationDataDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PackageCurationDataDao>(PackageCurationDataTable)

    var purl by PackageCurationDataTable.purl
    var cpe by PackageCurationDataTable.cpe
    var comment by PackageCurationDataTable.comment
    var description by PackageCurationDataTable.description
    var homepageUrl by PackageCurationDataTable.homepageUrl
    var isModified by PackageCurationDataTable.isModified
    var isMetadataOnly by PackageCurationDataTable.isMetadataOnly
    var binaryArtifact by RemoteArtifactDao optionalReferencedOn PackageCurationDataTable.binaryArtifactId
    var sourceArtifact by RemoteArtifactDao optionalReferencedOn PackageCurationDataTable.sourceArtifactId
    var vcs by CurationVcsInfoDao optionalReferencedOn PackageCurationDataTable.vcsId
    var concludedLicense by LicenseSpdxDao optionalReferencedOn PackageCurationDataTable.concludedLicenseSpdxId
    var authors by AuthorDao via PackageCurationDataAuthorsTable

    fun mapToModel() = PackageCurationData(
        id.value,
        comment,
        purl,
        cpe,
        authors.map(AuthorDao::mapToModel).toSet().ifEmpty { null },
        concludedLicense?.mapToModel(),
        description,
        homepageUrl,
        binaryArtifact?.mapToModel(),
        sourceArtifact?.mapToModel(),
        vcs?.mapToModel(),
        isMetadataOnly,
        isModified
    )
}
