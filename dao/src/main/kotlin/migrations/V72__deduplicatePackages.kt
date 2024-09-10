/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

@file:Suppress("Filename")

package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("ClassNaming")
internal class V72__deduplicatePackages : BaseJavaMigration() {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private var curIndex = -1L

    override fun migrate(context: Context?) {
        transaction {
            while (nextPackageId() != null) {
                val equalPackages = findEqualPackages(curIndex)

                logger.info(
                    "Found ${equalPackages.count()} equal packages for package with ID $curIndex: $equalPackages"
                )

                equalPackages.forEach {
                    updateReferences(curIndex, it)
                    deletePackage(it)
                }

                logger.info("Remaining packages: ${V72PackagesTable.selectAll().count()}")
            }
        }
    }

    /**
     * Find the next package ID that is greater than the [curIndex].
     */
    private fun nextPackageId() =
        V72PackagesTable.select(V72PackagesTable.id)
            .where { V72PackagesTable.id greater curIndex }
            .limit(1)
            .singleOrNull()
            ?.let {
                curIndex = it[V72PackagesTable.id].value
                curIndex
            }

    /**
     * Find all packages that are equal to the package with the provided [pkgId]. Equal means that not only the values
     * of the columns are equal, but also the references to other tables.
     */
    private fun findEqualPackages(pkgId: Long): List<Long> {
        val pkg = V72PackagesTable.selectAll().where { V72PackagesTable.id eq pkgId }.single()

        val authors = V72PackagesAuthorsTable.getForPackage(pkgId)
        val declaredLicenses = V72PackagesDeclaredLicensesTable.getForPackage(pkgId)
        val processedDeclaredLicenses = V72ProcessedDeclaredLicensesTable.getForPackage(pkgId)

        return V72PackagesTable.selectAll().where {
            V72PackagesTable.id neq pkgId and
                    (V72PackagesTable.identifierId eq pkg[V72PackagesTable.identifierId]) and
                    (V72PackagesTable.vcsId eq pkg[V72PackagesTable.vcsId]) and
                    (V72PackagesTable.vcsProcessedId eq pkg[V72PackagesTable.vcsProcessedId]) and
                    (V72PackagesTable.binaryArtifactId eq pkg[V72PackagesTable.binaryArtifactId]) and
                    (V72PackagesTable.sourceArtifactId eq pkg[V72PackagesTable.sourceArtifactId]) and
                    (V72PackagesTable.purl eq pkg[V72PackagesTable.purl]) and
                    (V72PackagesTable.cpe eq pkg[V72PackagesTable.cpe]) and
                    (V72PackagesTable.description eq pkg[V72PackagesTable.description]) and
                    (V72PackagesTable.homepageUrl eq pkg[V72PackagesTable.homepageUrl]) and
                    (V72PackagesTable.isMetadataOnly eq pkg[V72PackagesTable.isMetadataOnly]) and
                    (V72PackagesTable.isModified eq pkg[V72PackagesTable.isModified])
        }.map { it[V72PackagesTable.id].value }
            .filter { V72PackagesAuthorsTable.getForPackage(it) == authors }
            .filter { V72PackagesDeclaredLicensesTable.getForPackage(it) == declaredLicenses }
            .filter { V72ProcessedDeclaredLicensesTable.getForPackage(it) == processedDeclaredLicenses }
    }

    /**
     * Update all references to the [duplicatePkgId] to point to the [pkgId] instead.
     */
    private fun updateReferences(pkgId: Long, duplicatePkgId: Long) {
        V72PackagesAnalyzerRunsTable.update({ V72PackagesAnalyzerRunsTable.packageId eq duplicatePkgId }) {
            it[packageId] = pkgId
        }
    }

    /**
     * Delete the package with the provided [pkgId].
     */
    private fun deletePackage(pkgId: Long) {
        V72PackagesAuthorsTable.deleteWhere { packageId eq pkgId }
        V72PackagesDeclaredLicensesTable.deleteWhere { packageId eq pkgId }

        V72ProcessedDeclaredLicensesTable.select(V72ProcessedDeclaredLicensesTable.id)
            .where { V72ProcessedDeclaredLicensesTable.packageId eq pkgId }
            .forEach { processedDeclaredLicense ->
                val id = processedDeclaredLicense[V72ProcessedDeclaredLicensesTable.id].value

                V72ProcessedDeclaredLicensesMappedDeclaredLicensesTable.deleteWhere {
                    processedDeclaredLicenseId eq id
                }

                V72ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable.deleteWhere {
                    processedDeclaredLicenseId eq id
                }
            }

        V72ProcessedDeclaredLicensesTable.deleteWhere { packageId eq pkgId }

        V72PackagesTable.deleteWhere { id eq pkgId }
    }
}

internal object V72PackagesTable : LongIdTable("packages") {
    val identifierId = reference("identifier_id", V72IdentifiersTable)
    val vcsId = reference("vcs_id", V72VcsInfoTable)
    val vcsProcessedId = reference("vcs_processed_id", V72VcsInfoTable)
    val binaryArtifactId = reference("binary_artifact_id", V72RemoteArtifactsTable)
    val sourceArtifactId = reference("source_artifact_id", V72RemoteArtifactsTable)

    val purl = text("purl")
    val cpe = text("cpe").nullable()
    val description = text("description")
    val homepageUrl = text("homepage_url")
    val isMetadataOnly = bool("is_metadata_only").default(false)
    val isModified = bool("is_modified").default(false)
}

internal object V72IdentifiersTable : LongIdTable("identifiers") {
    val type = text("type")
    val namespace = text("namespace")
    val name = text("name")
    val version = text("version")
}

internal object V72VcsInfoTable : LongIdTable("vcs_info") {
    val type = text("type")
    val url = text("url")
    val revision = text("revision")
    val path = text("path")
}

internal object V72RemoteArtifactsTable : LongIdTable("remote_artifacts") {
    val url = text("url")
    val hashValue = text("hash_value")
    val hashAlgorithm = text("hash_algorithm")
}

internal object V72PackagesAuthorsTable : Table("packages_authors") {
    val authorId = reference("author_id", V72AuthorsTable)
    val packageId = reference("package_id", V72PackagesTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(authorId, packageId, name = "${tableName}_pkey")

    fun getForPackage(pkgId: Long): Set<Long> =
        select(authorId)
            .where { packageId eq pkgId }
            .orderBy(packageId)
            .mapTo(mutableSetOf()) { it[authorId].value }
}

internal object V72AuthorsTable : LongIdTable("authors") {
    val name = text("name")
}

internal object V72PackagesDeclaredLicensesTable : Table("packages_declared_licenses") {
    val packageId = reference("package_id", V72PackagesTable)
    val declaredLicenseId = reference("declared_license_id", V72DeclaredLicensesTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(packageId, declaredLicenseId, name = "${tableName}_pkey")

    fun getForPackage(pkgId: Long): Set<Long> =
        select(declaredLicenseId)
            .where { packageId eq pkgId }
            .orderBy(packageId)
            .mapTo(mutableSetOf()) { it[declaredLicenseId].value }
}

internal object V72DeclaredLicensesTable : LongIdTable("declared_licenses") {
    val name = text("name")
}

internal object V72ProcessedDeclaredLicensesTable : LongIdTable("processed_declared_licenses") {
    val packageId = reference("package_id", V72PackagesTable).nullable()
//    val projectId = reference("project_id", ProjectsTable).nullable()

    val spdxExpression = text("spdx_expression").nullable()

    fun getForPackage(pkgId: Long): Set<V72ProcessedDeclaredLicense> =
        selectAll()
            .where { packageId eq pkgId }
            .orderBy(packageId)
            .mapTo(mutableSetOf()) { processedDeclaredLicense ->
                val id = processedDeclaredLicense[id].value
                val spdxExpression = processedDeclaredLicense[spdxExpression]

                val mappedLicenses =
                    V72ProcessedDeclaredLicensesMappedDeclaredLicensesTable.getForProcessedDeclaredLicense(id)

                val unmappedLicenses =
                    V72ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable.getForProcessedDeclaredLicense(id)

                V72ProcessedDeclaredLicense(spdxExpression, mappedLicenses, unmappedLicenses)
            }
}

internal object V72ProcessedDeclaredLicensesMappedDeclaredLicensesTable :
    Table("processed_declared_licenses_mapped_declared_licenses") {
    val processedDeclaredLicenseId = reference("processed_declared_license_id", V72ProcessedDeclaredLicensesTable)
    val mappedDeclaredLicenseId = reference("mapped_declared_license_id", V72MappedDeclaredLicensesTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(processedDeclaredLicenseId, mappedDeclaredLicenseId, name = "${tableName}_pkey")

    fun getForProcessedDeclaredLicense(processedDeclaredLicense: Long) =
        (this innerJoin V72MappedDeclaredLicensesTable)
            .select(V72MappedDeclaredLicensesTable.declaredLicense, V72MappedDeclaredLicensesTable.mappedLicense)
            .where { processedDeclaredLicenseId eq processedDeclaredLicense }
            .associate {
                it[V72MappedDeclaredLicensesTable.declaredLicense] to it[V72MappedDeclaredLicensesTable.mappedLicense]
            }
}

internal object V72MappedDeclaredLicensesTable : LongIdTable("mapped_declared_licenses") {
    val declaredLicense = text("declared_license")
    val mappedLicense = text("mapped_license")
}

internal object V72ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable :
    Table("processed_declared_licenses_unmapped_declared_licenses") {
    val processedDeclaredLicenseId = reference("processed_declared_license_id", V72ProcessedDeclaredLicensesTable)
    val unmappedDeclaredLicenseId = reference("unmapped_declared_license_id", V72UnmappedDeclaredLicensesTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(processedDeclaredLicenseId, unmappedDeclaredLicenseId, name = "${tableName}_pkey")

    fun getForProcessedDeclaredLicense(processedDeclaredLicense: Long) =
        (this innerJoin V72UnmappedDeclaredLicensesTable)
            .select(V72UnmappedDeclaredLicensesTable.unmappedLicense)
            .where { processedDeclaredLicenseId eq processedDeclaredLicense }
            .mapTo(mutableSetOf()) { it[V72UnmappedDeclaredLicensesTable.unmappedLicense] }
}

internal object V72UnmappedDeclaredLicensesTable : LongIdTable("unmapped_declared_licenses") {
    val unmappedLicense = text("unmapped_license")
}

internal data class V72ProcessedDeclaredLicense(
    val spdxExpression: String?,
    val mappedLicenses: Map<String, String>,
    val unmappedLicenses: Set<String>
)

internal object V72PackagesAnalyzerRunsTable : Table("packages_analyzer_runs") {
    val packageId = reference("package_id", V72PackagesTable)
    val analyzerRunId = reference("analyzer_run_id", V72AnalyzerRunsTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(packageId, analyzerRunId, name = "${tableName}_pkey")
}

internal object V72AnalyzerRunsTable : LongIdTable("analyzer_runs")
