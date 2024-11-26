/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun

import org.eclipse.apoapsis.ortserver.dao.mapAndCompare
import org.eclipse.apoapsis.ortserver.dao.tables.shared.DeclaredLicenseDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.dao.utils.SortableEntityClass
import org.eclipse.apoapsis.ortserver.dao.utils.SortableTable
import org.eclipse.apoapsis.ortserver.model.runs.Package

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and

/**
 * A table to represent all metadata for a software package.
 */
object PackagesTable : SortableTable("packages") {
    val identifierId = reference("identifier_id", IdentifiersTable)
    val vcsId = reference("vcs_id", VcsInfoTable)
    val vcsProcessedId = reference("vcs_processed_id", VcsInfoTable)
    val binaryArtifactId = reference("binary_artifact_id", RemoteArtifactsTable)
    val sourceArtifactId = reference("source_artifact_id", RemoteArtifactsTable)

    val purl = text("purl").sortable()
    val cpe = text("cpe").nullable()
    val description = text("description")
    val homepageUrl = text("homepage_url")
    val isMetadataOnly = bool("is_metadata_only").default(false)
    val isModified = bool("is_modified").default(false)
}

class PackageDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : SortableEntityClass<PackageDao>(PackagesTable) {
        fun findByPackage(pkg: Package): PackageDao? =
            // TODO: Implement a more efficient way to check if an identical package already exists.
            find {
                PackagesTable.purl eq pkg.purl and
                        (PackagesTable.cpe eq pkg.cpe) and
                        (PackagesTable.description eq pkg.description) and
                        (PackagesTable.homepageUrl eq pkg.homepageUrl) and
                        (PackagesTable.isMetadataOnly eq pkg.isMetadataOnly) and
                        (PackagesTable.isModified eq pkg.isModified)
            }.firstOrNull {
                it.identifier.mapToModel() == pkg.identifier &&
                        mapAndCompare(it.authors, pkg.authors, AuthorDao::name) &&
                        mapAndCompare(it.declaredLicenses, pkg.declaredLicenses, DeclaredLicenseDao::name) &&
                        it.processedDeclaredLicense.mapToModel() == pkg.processedDeclaredLicense &&
                        it.vcs.mapToModel() == pkg.vcs &&
                        it.vcsProcessed.mapToModel() == pkg.vcsProcessed &&
                        it.binaryArtifact.mapToModel() == pkg.binaryArtifact &&
                        it.sourceArtifact.mapToModel() == pkg.sourceArtifact
            }
    }

    var identifier by IdentifierDao referencedOn PackagesTable.identifierId
    var vcs by VcsInfoDao referencedOn PackagesTable.vcsId
    var vcsProcessed by VcsInfoDao referencedOn PackagesTable.vcsProcessedId
    var binaryArtifact by RemoteArtifactDao referencedOn PackagesTable.binaryArtifactId
    var sourceArtifact by RemoteArtifactDao referencedOn PackagesTable.sourceArtifactId

    var purl by PackagesTable.purl
    var cpe by PackagesTable.cpe
    var description by PackagesTable.description
    var homepageUrl by PackagesTable.homepageUrl
    var isMetadataOnly by PackagesTable.isMetadataOnly
    var isModified by PackagesTable.isModified

    var authors by AuthorDao via PackagesAuthorsTable
    var declaredLicenses by DeclaredLicenseDao via PackagesDeclaredLicensesTable
    var analyzerRuns by AnalyzerRunDao via PackagesAnalyzerRunsTable

    val processedDeclaredLicense by ProcessedDeclaredLicenseDao backReferencedOn
            ProcessedDeclaredLicensesTable.packageId

    fun mapToModel() = Package(
        identifier = identifier.mapToModel(),
        purl = purl,
        cpe = cpe,
        authors = authors.mapTo(mutableSetOf()) { it.name },
        declaredLicenses = declaredLicenses.mapTo(mutableSetOf()) { it.name },
        processedDeclaredLicense = processedDeclaredLicense.mapToModel(),
        description = description,
        homepageUrl = homepageUrl,
        binaryArtifact = binaryArtifact.mapToModel(),
        sourceArtifact = sourceArtifact.mapToModel(),
        vcs = vcs.mapToModel(),
        vcsProcessed = vcsProcessed.mapToModel(),
        isMetadataOnly = isMetadataOnly,
        isModified = isModified
    )
}
