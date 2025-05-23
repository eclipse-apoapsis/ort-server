/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.queries.analyzer

import kotlin.collections.orEmpty

import org.eclipse.apoapsis.ortserver.dao.Query
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.MappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAuthorsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesMappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.UnmappedDeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.DeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo

import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.selectAll

/**
 * A query to get the [Package]s for a given [analyzerRunId]. Returns an empty set if no packages are found.
 */
class GetPackagesForAnalyzerRunQuery(
    /** The ID of the analyzer run to retrieve packages for. */
    val analyzerRunId: Long
) : Query<Set<Package>> {
    override fun execute(): Set<Package> {
        val packageIds = PackagesAnalyzerRunsTable
            .select(PackagesAnalyzerRunsTable.packageId)
            .where { PackagesAnalyzerRunsTable.analyzerRunId eq analyzerRunId }
            .mapTo(mutableSetOf()) { it[PackagesAnalyzerRunsTable.packageId].value }

        if (packageIds.isEmpty()) return emptySet()

        val vcsProcessedTable = VcsInfoTable.alias("vcs_processed_info")
        val binaryArtifacts = RemoteArtifactsTable.alias("binary_artifacts")
        val sourceArtifacts = RemoteArtifactsTable.alias("source_artifacts")

        val resultRows = PackagesTable
            .innerJoin(IdentifiersTable)
            .join(VcsInfoTable, JoinType.LEFT, PackagesTable.vcsId, VcsInfoTable.id)
            .join(vcsProcessedTable, JoinType.LEFT, PackagesTable.vcsProcessedId, vcsProcessedTable[VcsInfoTable.id])
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
            .selectAll()
            .where { PackagesTable.id inList packageIds }
            .toList()

        val authorsByPackageId = getAuthors(packageIds)
        val declaredLicensesByPackageId = getDeclaredLicenses(packageIds)
        val processedDeclaredLicenseByPackageId = getProcessedDeclaredLicenses(packageIds)

        return resultRows.mapTo(mutableSetOf()) { resultRow ->
            val pkgId = resultRow[PackagesTable.id].value

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
                purl = resultRow[PackagesTable.purl],
                cpe = resultRow[PackagesTable.cpe],
                authors = authorsByPackageId[pkgId].orEmpty(),
                declaredLicenses = declaredLicensesByPackageId[pkgId].orEmpty(),
                processedDeclaredLicense = processedDeclaredLicense,
                description = resultRow[PackagesTable.description],
                homepageUrl = resultRow[PackagesTable.homepageUrl],
                binaryArtifact = binaryArtifact,
                sourceArtifact = sourceArtifact,
                vcs = vcs,
                vcsProcessed = vcsProcessed,
                isMetadataOnly = resultRow[PackagesTable.isMetadataOnly],
                isModified = resultRow[PackagesTable.isModified]
            )
        }
    }

    /** Get the authors for the provided [packageIds]. */
    private fun getAuthors(packageIds: Set<Long>): Map<Long, Set<String>> =
        PackagesAuthorsTable
            .innerJoin(AuthorsTable)
            .select(PackagesAuthorsTable.packageId, AuthorsTable.name)
            .where { PackagesAuthorsTable.packageId inList packageIds }
            .groupBy({ it[PackagesAuthorsTable.packageId] }) { it[AuthorsTable.name] }
            .mapKeys { it.key.value }
            .mapValues { it.value.toSet() }

    /** Get the declared licenses for the provided [packageIds]. */
    private fun getDeclaredLicenses(packageIds: Set<Long>): Map<Long, Set<String>> =
        PackagesDeclaredLicensesTable
            .innerJoin(DeclaredLicensesTable)
            .select(PackagesDeclaredLicensesTable.packageId, DeclaredLicensesTable.name)
            .where { PackagesDeclaredLicensesTable.packageId inList packageIds }
            .groupBy({ it[PackagesDeclaredLicensesTable.packageId] }) { it[DeclaredLicensesTable.name] }
            .mapKeys { it.key.value }
            .mapValues { it.value.toSet() }

    /**
     * Get the [ProcessedDeclaredLicense]s for the provided [packageIds].
     */
    @Suppress("UNCHECKED_CAST")
    private fun getProcessedDeclaredLicenses(packageIds: Set<Long>): Map<Long, ProcessedDeclaredLicense?> =
        ProcessedDeclaredLicensesTable
            .leftJoin(ProcessedDeclaredLicensesMappedDeclaredLicensesTable)
            .leftJoin(MappedDeclaredLicensesTable)
            .leftJoin(ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable)
            .leftJoin(UnmappedDeclaredLicensesTable)
            .selectAll()
            .where { ProcessedDeclaredLicensesTable.packageId inList packageIds }
            .groupBy { it[ProcessedDeclaredLicensesTable.packageId]?.value }
            .mapValues { (_, resultRows) ->
                if (resultRows.isEmpty()) return@mapValues null

                // The compiler wrongly assumes that the declaredLicense, mappedLicense, and unmappedLicense columns
                // cannot be null, but they can be if there are no entries in the joined tables, so they must be cast
                // to nullable.
                val nullableMappedLicenses = resultRows.associate {
                    it[MappedDeclaredLicensesTable.declaredLicense] to
                            it[MappedDeclaredLicensesTable.mappedLicense]
                } as Map<String?, String>

                val mappedLicenses = nullableMappedLicenses.filterKeys { it != null } as Map<String, String>

                val unmappedLicenses = resultRows
                    .mapTo(mutableListOf<String?>()) { it[UnmappedDeclaredLicensesTable.unmappedLicense] }
                    .filterNotNullTo(mutableSetOf())

                ProcessedDeclaredLicense(
                    spdxExpression = resultRows[0][ProcessedDeclaredLicensesTable.spdxExpression],
                    mappedLicenses = mappedLicenses,
                    unmappedLicenses = unmappedLicenses
                )
            } as Map<Long, ProcessedDeclaredLicense?>
}
