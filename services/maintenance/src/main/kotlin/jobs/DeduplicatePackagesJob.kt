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

package org.eclipse.apoapsis.ortserver.services.maintenance.jobs

import java.sql.Connection

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.MappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.PackagesAuthorsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.PackagesDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.ProcessedDeclaredLicensesMappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.ProcessedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.UnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.model.MaintenanceJobData
import org.eclipse.apoapsis.ortserver.model.MaintenanceJobStatus
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.services.maintenance.MaintenanceJob
import org.eclipse.apoapsis.ortserver.services.maintenance.MaintenanceService

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

import org.slf4j.LoggerFactory

private val json = Json.Default
private val logger = LoggerFactory.getLogger(DeduplicatePackagesJob::class.java)

/**
 * Progress data for the [DeduplicatePackagesJob].
 */
@Serializable
private data class DeduplicatePackagesJobData(
    /** The last processed package ID. */
    val lastPackageId: Long,

    /** The number of unique deduplicated packages. */
    val deduplicatedPackages: Long,

    /** The number of removed duplicate packages. */
    val removedDuplicates: Long
)

/**
 * A maintenance job to deduplicate packages. The algorithm works as follows:
 *
 * 1. Find all packages that are equal to the package with the provided ID.
 * 2. Update all references to the duplicate packages to point to the original package.
 * 3. Delete the duplicate packages.
 * 4. Repeat until all packages are processed.
 */
class DeduplicatePackagesJob(private val db: Database) : MaintenanceJob() {
    override val name = "DeduplicatePackages"

    private var curIndex: Long = -1L
    private lateinit var curJobData: DeduplicatePackagesJobData

    override suspend fun execute(service: MaintenanceService, jobData: MaintenanceJobData) {
        curJobData = jobData.data?.let { deserializeJobData(it) } ?: DeduplicatePackagesJobData(-1L, 0, 0)
        curIndex = curJobData.lastPackageId

        while (nextPackageId() != null) {
            deduplicatePackage(service, jobData.id)
        }
    }

    private fun nextPackageId() = db.blockingQuery(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
        val minId = PackagesTable.id.min()

        PackagesTable.select(minId)
            .where { PackagesTable.id greater curIndex }
            .singleOrNull()
            ?.get(minId)?.let {
                curIndex = it.value
                curIndex
            }
    }

    /**
     * Find all duplicates of the package with the ID [curIndex], update references to point to this package instead,
     * and delete the duplicates.
     */
    private fun deduplicatePackage(service: MaintenanceService, jobId: Long) {
        db.blockingQuery {
            logger.info("Deduplicating package with ID $curIndex.")

            val equalPackages = findEqualPackages(curIndex)

            logger.info(
                "Found ${equalPackages.count()} equal packages for package with ID $curIndex: $equalPackages"
            )

            equalPackages.forEach {
                updateReferences(curIndex, it)
                deletePackage(it)
            }

            curJobData = DeduplicatePackagesJobData(
                lastPackageId = curIndex,
                deduplicatedPackages = curJobData.deduplicatedPackages + 1,
                removedDuplicates = curJobData.removedDuplicates + equalPackages.count()
            )

            logger.info(
                "Finished deduplicating package with ID $curIndex. Deduplicated ${curJobData.deduplicatedPackages} " +
                        "unique packages and removed ${curJobData.removedDuplicates} duplicates so far."
            )
        }

        val remainingPackages = countRemainingPackages()

        if (remainingPackages > 0L) {
            service.updateJob(jobId, serializeJobData(curJobData))
            logger.info("Remaining packages: $remainingPackages")
        } else {
            service.updateJob(jobId, serializeJobData(curJobData), MaintenanceJobStatus.FINISHED)
            logger.info(
                "Package deduplication finished. Deduplicated ${curJobData.deduplicatedPackages} unique packages and " +
                        "removed ${curJobData.removedDuplicates} duplicates."
            )
        }
    }

    /**
     * Find all packages that are equal to the package with the provided [pkgId]. Equal means that not only the values
     * of the columns are equal, but also the references to other tables.
     */
    private fun findEqualPackages(pkgId: Long): List<Long> {
        val pkg = PackagesTable.selectAll().where { PackagesTable.id eq pkgId }.single()

        val authors = PackagesAuthorsTable.getForPackage(pkgId)
        val declaredLicenses = PackagesDeclaredLicensesTable.getForPackage(pkgId)
        val processedDeclaredLicenses = ProcessedDeclaredLicensesTable.getForPackage(pkgId)

        return PackagesTable.selectAll().where {
            PackagesTable.id neq pkgId and
                    (PackagesTable.identifierId eq pkg[PackagesTable.identifierId]) and
                    (PackagesTable.vcsId eq pkg[PackagesTable.vcsId]) and
                    (PackagesTable.vcsProcessedId eq pkg[PackagesTable.vcsProcessedId]) and
                    (PackagesTable.binaryArtifactId eq pkg[PackagesTable.binaryArtifactId]) and
                    (PackagesTable.sourceArtifactId eq pkg[PackagesTable.sourceArtifactId]) and
                    (PackagesTable.purl eq pkg[PackagesTable.purl]) and
                    (PackagesTable.cpe eq pkg[PackagesTable.cpe]) and
                    (PackagesTable.description eq pkg[PackagesTable.description]) and
                    (PackagesTable.homepageUrl eq pkg[PackagesTable.homepageUrl]) and
                    (PackagesTable.isMetadataOnly eq pkg[PackagesTable.isMetadataOnly]) and
                    (PackagesTable.isModified eq pkg[PackagesTable.isModified])
        }.map { it[PackagesTable.id].value }
            .filter { PackagesAuthorsTable.getForPackage(it) == authors }
            .filter { PackagesDeclaredLicensesTable.getForPackage(it) == declaredLicenses }
            .filter { ProcessedDeclaredLicensesTable.getForPackage(it) == processedDeclaredLicenses }
    }

    /**
     * Update all references to the [duplicatePkgId] to point to the [pkgId] instead.
     */
    private fun updateReferences(pkgId: Long, duplicatePkgId: Long) {
        PackagesAnalyzerRunsTable.update({ PackagesAnalyzerRunsTable.packageId eq duplicatePkgId }) {
            it[packageId] = pkgId
        }
    }

    /**
     * Delete the package with the provided [pkgId].
     */
    private fun deletePackage(pkgId: Long) {
        logger.info("Deleting entries from packages_authors for package with ID $pkgId.")
        PackagesAuthorsTable.deleteWhere { packageId eq pkgId }
        logger.info("Deleting entries from packages_declared_licenses for package with ID $pkgId.")
        PackagesDeclaredLicensesTable.deleteWhere { packageId eq pkgId }

        logger.info("Deleting processed declared licenses for package with ID $pkgId.")
        ProcessedDeclaredLicensesTable.select(ProcessedDeclaredLicensesTable.id)
            .where { ProcessedDeclaredLicensesTable.packageId eq pkgId }
            .forEach { processedDeclaredLicense ->
                val id = processedDeclaredLicense[ProcessedDeclaredLicensesTable.id].value

                ProcessedDeclaredLicensesMappedDeclaredLicensesTable.deleteWhere {
                    processedDeclaredLicenseId eq id
                }

                ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable.deleteWhere {
                    processedDeclaredLicenseId eq id
                }
            }

        logger.info("Deleting declared licenses for package with ID $pkgId.")
        ProcessedDeclaredLicensesTable.deleteWhere { packageId eq pkgId }

        logger.info("Deleting package with ID $pkgId.")
        PackagesTable.deleteWhere { id eq pkgId }
    }

    /**
     * Count the number of remaining packages to process.
     */
    private fun countRemainingPackages() =
        db.blockingQuery(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
            PackagesTable.selectAll().where { PackagesTable.id greater curIndex }.count()
        }
}

private fun deserializeJobData(data: JsonObject) =
    try {
        json.decodeFromJsonElement<DeduplicatePackagesJobData>(data)
    } catch (e: SerializationException) {
        logger.error("Could not deserialize job data, starting from the beginning.", e)
        null
    }

private fun serializeJobData(data: DeduplicatePackagesJobData) = json.encodeToJsonElement(data).jsonObject

private fun PackagesAuthorsTable.getForPackage(pkgId: Long): Set<Long> =
    select(authorId)
        .where { packageId eq pkgId }
        .orderBy(packageId)
        .mapTo(mutableSetOf()) { it[authorId].value }

private fun PackagesDeclaredLicensesTable.getForPackage(pkgId: Long): Set<Long> =
    select(declaredLicenseId)
        .where { packageId eq pkgId }
        .orderBy(packageId)
        .mapTo(mutableSetOf()) { it[declaredLicenseId].value }

private fun ProcessedDeclaredLicensesTable.getForPackage(pkgId: Long): Set<ProcessedDeclaredLicense> =
    selectAll()
        .where { packageId eq pkgId }
        .orderBy(packageId)
        .mapTo(mutableSetOf()) { processedDeclaredLicense ->
            val id = processedDeclaredLicense[id].value
            val spdxExpression = processedDeclaredLicense[spdxExpression]

            val mappedLicenses =
                ProcessedDeclaredLicensesMappedDeclaredLicensesTable.getForProcessedDeclaredLicense(id)

            val unmappedLicenses =
                ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable.getForProcessedDeclaredLicense(id)

            ProcessedDeclaredLicense(spdxExpression, mappedLicenses, unmappedLicenses)
        }

private fun ProcessedDeclaredLicensesMappedDeclaredLicensesTable.getForProcessedDeclaredLicense(
    processedDeclaredLicense: Long
) = (this innerJoin MappedDeclaredLicensesTable)
    .select(MappedDeclaredLicensesTable.declaredLicense, MappedDeclaredLicensesTable.mappedLicense)
    .where { processedDeclaredLicenseId eq processedDeclaredLicense }
    .associate {
        it[MappedDeclaredLicensesTable.declaredLicense] to it[MappedDeclaredLicensesTable.mappedLicense]
    }

private fun ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable.getForProcessedDeclaredLicense(
    processedDeclaredLicense: Long
) = (this innerJoin UnmappedDeclaredLicensesTable)
    .select(UnmappedDeclaredLicensesTable.unmappedLicense)
    .where { processedDeclaredLicenseId eq processedDeclaredLicense }
    .mapTo(mutableSetOf()) { it[UnmappedDeclaredLicensesTable.unmappedLicense] }
