/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.services.ortrun

import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityRating
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityReference
import org.eclipse.apoapsis.ortserver.components.projections.ProjectionBuilder
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.AdvisorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorResultsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorResultsVulnerabilitiesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorRunsIdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.ResolvedVulnerabilitiesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.VulnerabilitiesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.VulnerabilityReferencesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.VulnerabilityResolutionsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.apimodel.Identifier
import org.eclipse.apoapsis.ortserver.shared.apimodel.VulnerabilityResolution

import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * The table of the projection of the vulnerabilities of an ORT run. It contains one row for each combination of a
 * vulnerability, an affected package, and the advisor that reported it.
 */
internal object ProjectedRunVulnerabilitiesTable : Table("projected_run_vulnerabilities") {
    val ortRunId = long("ort_run_id")
    val vulnerabilityId = long("vulnerability_id")
    val identifierId = long("identifier_id")
    val advisorName = text("advisor_name")
    val externalId = text("external_id")
    val summary = text("summary").nullable()
    val description = text("description").nullable()
    val rating = integer("rating")
    val purl = text("purl")
    val identifierType = text("identifier_type")
    val identifierNamespace = text("identifier_namespace")
    val identifierName = text("identifier_name")
    val identifierVersion = text("identifier_version")
    val firstFixedVersions = jsonb<Set<String>>("first_fixed_versions")
    val fixAvailable = bool("fix_available")
    val references = jsonb<List<VulnerabilityReference>>("vulnerability_references")
    val resolutions = jsonb<List<VulnerabilityResolution>>("resolutions")
    val resolved = bool("resolved")

    override val primaryKey = PrimaryKey(ortRunId, vulnerabilityId, identifierId, advisorName)
}

/**
 * A [ProjectionBuilder] for the projection of the vulnerabilities of an ORT run. To limit the memory used for large
 * runs, the projection is built in chunks of [chunkSize] affected packages.
 */
class RunVulnerabilitiesProjectionBuilder(private val chunkSize: Int = DEFAULT_CHUNK_SIZE) : ProjectionBuilder {
    companion object {
        /** The type of the projection. */
        const val TYPE = "run-vulnerabilities"

        /** The current version of the schema of the projection. */
        const val SCHEMA_VERSION = 1

        /** The default number of affected packages for which the projection is built at once. */
        const val DEFAULT_CHUNK_SIZE = 500
    }

    init {
        require(chunkSize > 0) { "The chunk size must be positive, but was $chunkSize." }
    }

    override val type = TYPE

    override val schemaVersion = SCHEMA_VERSION

    override suspend fun build(ortRunId: Long) {
        ProjectedRunVulnerabilitiesTable.deleteWhere { ProjectedRunVulnerabilitiesTable.ortRunId eq ortRunId }

        val identifierIds = selectFromRun(ortRunId, IdentifiersTable.id)
            .withDistinct()
            .orderBy(IdentifiersTable.id)
            .map { it[IdentifiersTable.id].value }

        identifierIds.chunked(chunkSize).forEach { chunk -> buildChunk(ortRunId, chunk) }
    }

    /** Build the projection for the affected packages with the given [identifierIds] of the run with [ortRunId]. */
    private fun buildChunk(ortRunId: Long, identifierIds: List<Long>) {
        val vulnerabilityRows = selectFromRun(
            ortRunId,
            VulnerabilitiesTable.id,
            VulnerabilitiesTable.externalId,
            VulnerabilitiesTable.summary,
            VulnerabilitiesTable.description,
            VulnerabilitiesTable.firstFixedVersions,
            IdentifiersTable.id,
            IdentifiersTable.type,
            IdentifiersTable.namespace,
            IdentifiersTable.name,
            IdentifiersTable.version,
            AdvisorResultsTable.advisorName
        ).andWhere { IdentifiersTable.id inList identifierIds }
            .withDistinct()
            .toList()

        val referencesByVulnerabilityId =
            fetchReferences(vulnerabilityRows.mapTo(mutableSetOf()) { it[VulnerabilitiesTable.id].value })
        val purlsByIdentifierId = fetchPurls(ortRunId, identifierIds)
        val resolutionsByVulnerabilityAndIdentifier = fetchResolutions(ortRunId, identifierIds)

        ProjectedRunVulnerabilitiesTable.batchInsert(vulnerabilityRows, shouldReturnGeneratedValues = false) { row ->
            val vulnerabilityId = row[VulnerabilitiesTable.id].value
            val identifierId = row[IdentifiersTable.id].value
            val identifier = Identifier(
                type = row[IdentifiersTable.type],
                namespace = row[IdentifiersTable.namespace],
                name = row[IdentifiersTable.name],
                version = row[IdentifiersTable.version]
            )
            val references = referencesByVulnerabilityId[vulnerabilityId].orEmpty()
            val resolutions = resolutionsByVulnerabilityAndIdentifier[vulnerabilityId to identifierId].orEmpty()
            val firstFixedVersions = row[VulnerabilitiesTable.firstFixedVersions]

            this[ProjectedRunVulnerabilitiesTable.ortRunId] = ortRunId
            this[ProjectedRunVulnerabilitiesTable.vulnerabilityId] = vulnerabilityId
            this[ProjectedRunVulnerabilitiesTable.identifierId] = identifierId
            this[ProjectedRunVulnerabilitiesTable.advisorName] = row[AdvisorResultsTable.advisorName]
            this[ProjectedRunVulnerabilitiesTable.externalId] = row[VulnerabilitiesTable.externalId]
            this[ProjectedRunVulnerabilitiesTable.summary] = row[VulnerabilitiesTable.summary]
            this[ProjectedRunVulnerabilitiesTable.description] = row[VulnerabilitiesTable.description]
            this[ProjectedRunVulnerabilitiesTable.rating] = references.overallRating().ordinal
            this[ProjectedRunVulnerabilitiesTable.purl] =
                purlsByIdentifierId[identifierId].orEmpty().ifEmpty { identifier.toFallbackPurl() }
            this[ProjectedRunVulnerabilitiesTable.identifierType] = identifier.type
            this[ProjectedRunVulnerabilitiesTable.identifierNamespace] = identifier.namespace
            this[ProjectedRunVulnerabilitiesTable.identifierName] = identifier.name
            this[ProjectedRunVulnerabilitiesTable.identifierVersion] = identifier.version
            this[ProjectedRunVulnerabilitiesTable.firstFixedVersions] = firstFixedVersions
            this[ProjectedRunVulnerabilitiesTable.fixAvailable] = firstFixedVersions.isNotEmpty()
            this[ProjectedRunVulnerabilitiesTable.references] = references
            this[ProjectedRunVulnerabilitiesTable.resolutions] = resolutions
            this[ProjectedRunVulnerabilitiesTable.resolved] = resolutions.isNotEmpty()
        }
    }

    /** Select the given [columns] for all vulnerabilities found by the advisor in the ORT run with the [ortRunId]. */
    private fun selectFromRun(ortRunId: Long, vararg columns: Expression<*>): Query =
        VulnerabilitiesTable
            .innerJoin(AdvisorResultsVulnerabilitiesTable)
            .innerJoin(AdvisorResultsTable)
            .innerJoin(AdvisorRunsIdentifiersTable)
            .innerJoin(AdvisorRunsTable)
            .innerJoin(AdvisorJobsTable)
            .innerJoin(OrtRunsTable)
            .innerJoin(IdentifiersTable)
            .select(columns.toList())
            .where { OrtRunsTable.id eq ortRunId }

    private fun fetchReferences(vulnerabilityIds: Set<Long>): Map<Long, List<VulnerabilityReference>> =
        VulnerabilityReferencesTable.selectAll()
            .where { VulnerabilityReferencesTable.vulnerabilityId inList vulnerabilityIds }
            .orderBy(VulnerabilityReferencesTable.id)
            .groupBy(
                { it[VulnerabilityReferencesTable.vulnerabilityId].value },
                {
                    VulnerabilityReference(
                        url = it[VulnerabilityReferencesTable.url],
                        scoringSystem = it[VulnerabilityReferencesTable.scoringSystem],
                        severity = it[VulnerabilityReferencesTable.severity],
                        score = it[VulnerabilityReferencesTable.score],
                        vector = it[VulnerabilityReferencesTable.vector]
                    )
                }
            )

    private fun fetchPurls(ortRunId: Long, identifierIds: List<Long>): Map<Long, String> {
        val runIdAlias = OrtRunsTable.id.alias("ri_ort_run_id")
        val identifierIdAlias = IdentifiersTable.id.alias("ri_identifier_id")
        val runIdentifierPairs = selectFromRun(ortRunId, runIdAlias, identifierIdAlias)
            .andWhere { IdentifiersTable.id inList identifierIds }
            .withDistinct()
            .alias("run_identifier_pairs")

        val purls = buildPurlByRunIdentifier(
            runIdentifierPairs,
            runIdentifierPairs[runIdAlias],
            runIdentifierPairs[identifierIdAlias]
        )

        return purls.alias
            .select(purls.alias[purls.identifierId], purls.alias[purls.purl])
            .associate { it[purls.alias[purls.identifierId]].value to it[purls.alias[purls.purl]] }
    }

    private fun fetchResolutions(
        ortRunId: Long,
        identifierIds: List<Long>
    ): Map<Pair<Long, Long>, List<VulnerabilityResolution>> =
        ResolvedVulnerabilitiesTable
            .innerJoin(VulnerabilityResolutionsTable, { vulnerabilityResolutionId }, { id })
            .select(
                ResolvedVulnerabilitiesTable.vulnerabilityId,
                ResolvedVulnerabilitiesTable.identifierId,
                VulnerabilityResolutionsTable.externalId,
                VulnerabilityResolutionsTable.reason,
                VulnerabilityResolutionsTable.comment,
                VulnerabilityResolutionsTable.resolutionSource
            )
            .where {
                (ResolvedVulnerabilitiesTable.ortRunId eq ortRunId) and
                    (ResolvedVulnerabilitiesTable.identifierId inList identifierIds)
            }
            .orderBy(ResolvedVulnerabilitiesTable.id)
            .groupBy(
                {
                    it[ResolvedVulnerabilitiesTable.vulnerabilityId].value to
                            it[ResolvedVulnerabilitiesTable.identifierId].value
                },
                {
                    VulnerabilityResolution(
                        externalId = it[VulnerabilityResolutionsTable.externalId],
                        reason = it[VulnerabilityResolutionsTable.reason].mapToApi(),
                        comment = it[VulnerabilityResolutionsTable.comment],
                        source = it[VulnerabilityResolutionsTable.resolutionSource].mapToApi()
                    )
                }
            )
}

/** Return the overall rating of a vulnerability, which is the highest rating of its references. */
private fun List<VulnerabilityReference>.overallRating(): VulnerabilityRating =
    maxOfOrNull { reference ->
        when (reference.severity?.uppercase()) {
            "CRITICAL" -> VulnerabilityRating.CRITICAL
            "HIGH" -> VulnerabilityRating.HIGH
            "MEDIUM" -> VulnerabilityRating.MEDIUM
            "LOW" -> VulnerabilityRating.LOW
            else -> VulnerabilityRating.NONE
        }
    } ?: VulnerabilityRating.NONE
