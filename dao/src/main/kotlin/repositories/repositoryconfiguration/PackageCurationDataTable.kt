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

package org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration

import org.eclipse.apoapsis.ortserver.dao.mapAndDeduplicate
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AuthorDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactsTable
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCurationData

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

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
    val hasAuthors = bool("has_authors")
}

class PackageCurationDataDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PackageCurationDataDao>(PackageCurationDataTable) {
        fun findByPackageCurationData(data: PackageCurationData): PackageCurationDataDao? =
            find {
                with(PackageCurationDataTable) {
                    this.comment eq data.comment and
                            (this.purl eq data.purl) and
                            (this.cpe eq data.cpe) and
                            (this.concludedLicense eq data.concludedLicense) and
                            (this.description eq data.description) and
                            (this.homepageUrl eq data.homepageUrl) and
                            (this.isMetadataOnly eq data.isMetadataOnly) and
                            (this.isModified eq data.isModified)
                }
            }.firstOrNull {
                it.binaryArtifact?.mapToModel() == data.binaryArtifact &&
                        it.sourceArtifact?.mapToModel() == data.sourceArtifact &&
                        it.vcsInfoCurationData?.mapToModel() == data.vcs &&
                        it.authors.takeIf { _ -> it.hasAuthors }
                            ?.mapTo(mutableSetOf()) { author -> author.name } == data.authors &&
                        it.declaredLicenseMappings
                            .associate { pair -> pair.license to pair.spdxLicense } == data.declaredLicenseMapping
            }

        fun getOrPut(data: PackageCurationData): PackageCurationDataDao =
            findByPackageCurationData(data) ?: new {
                this.comment = data.comment
                this.purl = data.purl
                this.cpe = data.cpe
                this.concludedLicense = data.concludedLicense
                this.description = data.description
                this.homepageUrl = data.homepageUrl
                this.isMetadataOnly = data.isMetadataOnly
                this.isModified = data.isModified
                this.binaryArtifact = data.binaryArtifact?.let { RemoteArtifactDao.getOrPut(it) }
                this.sourceArtifact = data.sourceArtifact?.let { RemoteArtifactDao.getOrPut(it) }
                this.vcsInfoCurationData = data.vcs?.let { VcsInfoCurationDataDao.getOrPut(it) }
                this.authors = mapAndDeduplicate(data.authors, AuthorDao::getOrPut)
                this.hasAuthors = data.authors != null
                this.declaredLicenseMappings = mapAndDeduplicate(data.declaredLicenseMapping.entries) {
                    DeclaredLicenseMappingDao.getOrPut(it.key, it.value)
                }
            }
    }

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
    var hasAuthors by PackageCurationDataTable.hasAuthors
    var declaredLicenseMappings by DeclaredLicenseMappingDao via PackageCurationDataDeclaredLicenseMappingsTable

    fun mapToModel() = PackageCurationData(
        comment = comment,
        purl = purl,
        cpe = cpe,
        authors = if (hasAuthors) authors.mapTo(mutableSetOf()) { it.name } else null,
        concludedLicense = concludedLicense,
        description = description,
        homepageUrl = homepageUrl,
        binaryArtifact = binaryArtifact?.mapToModel(),
        sourceArtifact = sourceArtifact?.mapToModel(),
        vcs = vcsInfoCurationData?.mapToModel(),
        isMetadataOnly = isMetadataOnly,
        isModified = isModified,
        declaredLicenseMapping = declaredLicenseMappings.associate { it.license to it.spdxLicense }
    )
}
