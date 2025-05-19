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

import org.eclipse.apoapsis.ortserver.dao.tables.shared.DeclaredLicenseDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.DeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.dao.utils.ArrayAggColumnEquals
import org.eclipse.apoapsis.ortserver.dao.utils.ArrayAggTwoColumnsEquals
import org.eclipse.apoapsis.ortserver.dao.utils.SortableEntityClass
import org.eclipse.apoapsis.ortserver.dao.utils.SortableTable
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.andHaving
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll

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

    /** Get the [Package]s for the provided [packageIds]. */
    fun getByIds(packageIds: Set<Long>): Set<Package> {
        val vcsProcessedTable = VcsInfoTable.alias("vcs_processed_info")
        val binaryArtifacts = RemoteArtifactsTable.alias("binary_artifacts")
        val sourceArtifacts = RemoteArtifactsTable.alias("source_artifacts")

        val resultRows = innerJoin(IdentifiersTable)
            .join(VcsInfoTable, JoinType.LEFT, vcsId, VcsInfoTable.id)
            .join(vcsProcessedTable, JoinType.LEFT, vcsProcessedId, vcsProcessedTable[VcsInfoTable.id])
            .join(
                binaryArtifacts,
                JoinType.LEFT,
                binaryArtifactId,
                binaryArtifacts[RemoteArtifactsTable.id]
            )
            .join(
                sourceArtifacts,
                JoinType.LEFT,
                sourceArtifactId,
                sourceArtifacts[RemoteArtifactsTable.id]
            )
            .selectAll()
            .where { id inList packageIds }
            .toList()

        val authorsByPackageId = PackagesAuthorsTable.getAuthorsByPackageIds(packageIds)
        val declaredLicensesByPackageId = PackagesDeclaredLicensesTable.getDeclaredLicensesByPackageIds(packageIds)
        val processedDeclaredLicenseByPackageId = ProcessedDeclaredLicensesTable.getByPackageIds(packageIds)

        return resultRows.mapTo(mutableSetOf()) { resultRow ->
            val pkgId = resultRow[id].value

            val identifier = Identifier(
                type = resultRow[IdentifiersTable.type],
                namespace = resultRow[IdentifiersTable.namespace],
                name = resultRow[IdentifiersTable.name],
                version = resultRow[IdentifiersTable.version]
            )

            val processedDeclaredLicense = processedDeclaredLicenseByPackageId[pkgId] ?: ProcessedDeclaredLicense(
                spdxExpression = null,
                mappedLicenses = emptyMap(),
                unmappedLicenses = emptySet()
            )

            val binaryArtifact = RemoteArtifact(
                url = resultRow[binaryArtifacts[RemoteArtifactsTable.url]],
                hashValue = resultRow[binaryArtifacts[RemoteArtifactsTable.hashValue]],
                hashAlgorithm = resultRow[binaryArtifacts[RemoteArtifactsTable.hashAlgorithm]]
            )

            val sourceArtifact = RemoteArtifact(
                url = resultRow[sourceArtifacts[RemoteArtifactsTable.url]],
                hashValue = resultRow[sourceArtifacts[RemoteArtifactsTable.hashValue]],
                hashAlgorithm = resultRow[sourceArtifacts[RemoteArtifactsTable.hashAlgorithm]]
            )

            val vcs = VcsInfo(
                type = RepositoryType.forName(resultRow[VcsInfoTable.type]),
                url = resultRow[VcsInfoTable.url],
                revision = resultRow[VcsInfoTable.revision],
                path = resultRow[VcsInfoTable.path]
            )

            val vcsProcessed = VcsInfo(
                type = RepositoryType.forName(resultRow[vcsProcessedTable[VcsInfoTable.type]]),
                url = resultRow[vcsProcessedTable[VcsInfoTable.url]],
                revision = resultRow[vcsProcessedTable[VcsInfoTable.revision]],
                path = resultRow[vcsProcessedTable[VcsInfoTable.path]]
            )

            Package(
                identifier = identifier,
                purl = resultRow[purl],
                cpe = resultRow[cpe],
                authors = authorsByPackageId[pkgId].orEmpty(),
                declaredLicenses = declaredLicensesByPackageId[pkgId].orEmpty(),
                processedDeclaredLicense = processedDeclaredLicense,
                description = resultRow[description],
                homepageUrl = resultRow[homepageUrl],
                binaryArtifact = binaryArtifact,
                sourceArtifact = sourceArtifact,
                vcs = vcs,
                vcsProcessed = vcsProcessed,
                isMetadataOnly = resultRow[isMetadataOnly],
                isModified = resultRow[isModified]
            )
        }
    }
}

class PackageDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : SortableEntityClass<PackageDao>(PackagesTable) {
        fun findByPackage(pkg: Package): PackageDao? {
            val vcsProcessed = VcsInfoTable.alias("vcs_processed_info")
            val binaryArtifacts = RemoteArtifactsTable.alias("binary_artifacts")
            val sourceArtifacts = RemoteArtifactsTable.alias("source_artifacts")
            val query = PackagesTable
                .leftJoin(IdentifiersTable)
                .join(VcsInfoTable, JoinType.LEFT, onColumn = PackagesTable.vcsId, otherColumn = VcsInfoTable.id)
                .join(vcsProcessed, JoinType.LEFT, PackagesTable.vcsProcessedId, vcsProcessed[VcsInfoTable.id])
                .join(
                    binaryArtifacts,
                    JoinType.LEFT,
                    PackagesTable.binaryArtifactId,
                    binaryArtifacts[RemoteArtifactsTable.id]
                )
                .join(
                    sourceArtifacts,
                    JoinType.LEFT,
                    PackagesTable.sourceArtifactId,
                    sourceArtifacts[RemoteArtifactsTable.id]
                )
                .leftJoin(PackagesAuthorsTable)
                .leftJoin(AuthorsTable)
                .leftJoin(PackagesDeclaredLicensesTable)
                .leftJoin(DeclaredLicensesTable)
                .leftJoin(ProcessedDeclaredLicensesTable)
                .leftJoin(ProcessedDeclaredLicensesMappedDeclaredLicensesTable)
                .leftJoin(MappedDeclaredLicensesTable)
                .leftJoin(ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable)
                .leftJoin(UnmappedDeclaredLicensesTable)
                .select(PackagesTable.id)
                .where { PackagesTable.purl eq pkg.purl }
                .andWhere { PackagesTable.cpe eq pkg.cpe }
                .andWhere { PackagesTable.description eq pkg.description }
                .andWhere { PackagesTable.homepageUrl eq pkg.homepageUrl }
                .andWhere { PackagesTable.isMetadataOnly eq pkg.isMetadataOnly }
                .andWhere { PackagesTable.isModified eq pkg.isModified }
                .andWhere { IdentifiersTable.type eq pkg.identifier.type }
                .andWhere { IdentifiersTable.namespace eq pkg.identifier.namespace }
                .andWhere { IdentifiersTable.name eq pkg.identifier.name }
                .andWhere { IdentifiersTable.version eq pkg.identifier.version }
                .andWhere { VcsInfoTable.type eq pkg.vcs.type.name }
                .andWhere { VcsInfoTable.url eq pkg.vcs.url }
                .andWhere { VcsInfoTable.revision eq pkg.vcs.revision }
                .andWhere { VcsInfoTable.path eq pkg.vcs.path }
                .andWhere { vcsProcessed[VcsInfoTable.type] eq pkg.vcsProcessed.type.name }
                .andWhere { vcsProcessed[VcsInfoTable.url] eq pkg.vcsProcessed.url }
                .andWhere { vcsProcessed[VcsInfoTable.revision] eq pkg.vcsProcessed.revision }
                .andWhere { vcsProcessed[VcsInfoTable.path] eq pkg.vcsProcessed.path }
                .andWhere { binaryArtifacts[RemoteArtifactsTable.url] eq pkg.binaryArtifact.url }
                .andWhere { binaryArtifacts[RemoteArtifactsTable.hashValue] eq pkg.binaryArtifact.hashValue }
                .andWhere { binaryArtifacts[RemoteArtifactsTable.hashAlgorithm] eq pkg.binaryArtifact.hashAlgorithm }
                .andWhere { sourceArtifacts[RemoteArtifactsTable.url] eq pkg.sourceArtifact.url }
                .andWhere { sourceArtifacts[RemoteArtifactsTable.hashValue] eq pkg.sourceArtifact.hashValue }
                .andWhere { sourceArtifacts[RemoteArtifactsTable.hashAlgorithm] eq pkg.sourceArtifact.hashAlgorithm }
                .andWhere {
                    ProcessedDeclaredLicensesTable.spdxExpression eq pkg.processedDeclaredLicense.spdxExpression
                }
                .groupBy(
                    PackagesTable.id,
                    IdentifiersTable.id,
                    VcsInfoTable.id,
                    vcsProcessed[VcsInfoTable.id],
                    binaryArtifacts[RemoteArtifactsTable.id],
                    sourceArtifacts[RemoteArtifactsTable.id]
                )
                .having { ArrayAggColumnEquals(AuthorsTable.name, pkg.authors) }
                .andHaving { ArrayAggColumnEquals(DeclaredLicensesTable.name, pkg.declaredLicenses) }
                .andHaving {
                    ArrayAggColumnEquals(
                        UnmappedDeclaredLicensesTable.unmappedLicense,
                        pkg.processedDeclaredLicense.unmappedLicenses
                    )
                }
                .andHaving {
                    ArrayAggTwoColumnsEquals(
                        MappedDeclaredLicensesTable.declaredLicense,
                        MappedDeclaredLicensesTable.mappedLicense,
                        pkg.processedDeclaredLicense.mappedLicenses
                    )
                }

            val id = query.firstOrNull()?.let { it[PackagesTable.id] } ?: return null

            return PackageDao[id]
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
