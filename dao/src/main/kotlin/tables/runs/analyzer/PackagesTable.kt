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
import org.ossreviewtoolkit.server.dao.tables.runs.shared.LicenseSpdxDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.LicenseSpdxTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.LicenseStringDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactsTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoTable

/**
 * A table to represent all metadata for a software package.
 */
object PackagesTable : LongIdTable("packages") {
    val identifier = reference("identifier_id", IdentifiersTable.id, ReferenceOption.CASCADE)
    val vcs = reference("vcs_id", VcsInfoTable.id, ReferenceOption.CASCADE)
    val vcsProcessed = reference("vcs_processed_id", VcsInfoTable.id, ReferenceOption.CASCADE)
    val binaryArtifact = reference("binary_artifact_id", RemoteArtifactsTable.id, ReferenceOption.CASCADE)
    val sourceArtifact = reference("source_artifact_id", RemoteArtifactsTable.id, ReferenceOption.CASCADE)
    val concludedLicense = reference("concluded_license_spdx_id", LicenseSpdxTable.id, ReferenceOption.CASCADE)
    val processedDeclaredLicense = reference(
        "processed_declared_license_id",
        ProcessedDeclaredLicensesTable.id,
        ReferenceOption.CASCADE
    )

    val cpe = text("cpe").nullable()
    val purl = text("purl")
    val description = text("description")
    val homepageUrl = text("homepage_url")
    val isMetadataOnly = bool("is_metadata_only").default(false)
    val isModified = bool("is_modified").default(false)
}

class PackageDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PackageDao>(PackagesTable)

    var identifier by IdentifierDao referencedOn PackagesTable.identifier
    var vcs by VcsInfoDao referencedOn PackagesTable.vcs
    var vcsProcessed by VcsInfoDao referencedOn PackagesTable.vcsProcessed
    var authors by AuthorDao via PackagesAuthorsTable
    var binaryArtifact by RemoteArtifactDao referencedOn PackagesTable.binaryArtifact
    var sourceArtifact by RemoteArtifactDao referencedOn PackagesTable.sourceArtifact
    var concludedLicense by LicenseSpdxDao referencedOn PackagesTable.concludedLicense
    var declaredLicenses by LicenseStringDao via PackagesDeclaredLicensesTable
    var processedDeclaredLicense by ProcessedDeclaredLicenseDao referencedOn PackagesTable.processedDeclaredLicense

    var cpe by PackagesTable.cpe
    var purl by PackagesTable.purl
    var description by PackagesTable.description
    var homepageUrl by PackagesTable.homepageUrl
    var isMetadataOnly by PackagesTable.isMetadataOnly
    var isModified by PackagesTable.isModified
}
