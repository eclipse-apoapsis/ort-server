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

package org.ossreviewtoolkit.server.dao.tables.runs.repository

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.AuthorDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactsTable

/**
 * A table to represent a package configuration data, which is part of a [PackageCuration][PackageCurationsTable].
 */
object PackageCurationDataTable : LongIdTable("package_curation_data") {
    val binaryArtifactId = reference("binary_artifact_id", RemoteArtifactsTable).nullable()
    val sourceArtifactId = reference("source_artifact_id", RemoteArtifactsTable).nullable()
    val vcsInfoCurationDataId = reference("vcs_info_curation_data_id", VcsInfoCurationDataTable).nullable()

    val comment = text("comment").nullable()
    val purl = text("purl").nullable()
    val cpe = text("cpe").nullable()
    val concludedLicense = text("concluded_license").nullable()
    val description = text("description").nullable()
    val homepageUrl = text("homepage_url").nullable()
    val isMetadataOnly = bool("is_metadata_only").nullable()
    val isModified = bool("is_modified").nullable()
}

class PackageCurationDataDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PackageCurationDataDao>(PackageCurationDataTable)

    var binaryArtifact by RemoteArtifactDao optionalReferencedOn PackageCurationDataTable.binaryArtifactId
    var sourceArtifact by RemoteArtifactDao optionalReferencedOn PackageCurationDataTable.sourceArtifactId
    var vcsInfoCurationData by VcsInfoCurationDataDao optionalReferencedOn
            PackageCurationDataTable.vcsInfoCurationDataId

    var comment by PackageCurationDataTable.comment
    var purl by PackageCurationDataTable.purl
    var cpe by PackageCurationDataTable.cpe
    var concludedLicense by PackageCurationDataTable.concludedLicense
    var description by PackageCurationDataTable.description
    var homepageUrl by PackageCurationDataTable.homepageUrl
    var isMetadataOnly by PackageCurationDataTable.isMetadataOnly
    var isModified by PackageCurationDataTable.isModified

    var authors by AuthorDao via PackageCurationDataAuthors
    var declaredLicenseMappings by DeclaredLicenseMappingDao via PackageCurationDataDeclaredLicenseMappingsTable
}
