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

package org.eclipse.apoapsis.ortserver.components.search.backend

import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.components.search.apimodel.RunWithPackage
import org.eclipse.apoapsis.ortserver.components.search.apimodel.RunWithVulnerability
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.AdvisorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorResultsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorResultsVulnerabilitiesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorRunsIdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.AdvisorRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.VulnerabilitiesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDataTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedConfigurationsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedPackageCurationProvidersTable
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.ResolvedPackageCurationsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.utils.apply
import org.eclipse.apoapsis.ortserver.dao.utils.applyIRegex
import org.eclipse.apoapsis.ortserver.dao.utils.extractIds
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyLevel
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter

import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Join
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.concat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select

class SearchService(
    private val db: Database,
    private val authorizationService: AuthorizationService
) {
    /**
     * Search for Analyzer runs containing the given package identifier or PURL, with optional scoping.
     *
     * @param identifier The package identifier regex to search for (ORT format: type:namespace:name:version).
     * @param purl The package URL regex to search for. When provided, searches against effective (post-curation) PURLs.
     * @param userId The user ID performing the search.
     * @param scope Optional scope to limit the search. Can be an [OrganizationId], [ProductId], or [RepositoryId].
     *              If null, performs a global search (requires superuser permission).
     */
    suspend fun findOrtRunsByPackage(
        identifier: String?,
        purl: String?,
        userId: String,
        scope: HierarchyId? = null
    ): List<RunWithPackage> {
        require((identifier == null) != (purl == null)) {
            "Exactly one of 'identifier' or 'purl' must be provided."
        }

        val hierarchyFilter = authorizationService.filterHierarchyIds(
            userId = userId,
            repositoryPermissions = setOf(RepositoryPermission.READ),
            containedIn = scope
        )

        return db.blockingQuery {
            val query = OrtRunsTable
                .innerJoin(AnalyzerJobsTable, { OrtRunsTable.id }, { ortRunId })
                .innerJoin(AnalyzerRunsTable, { AnalyzerJobsTable.id }, { analyzerJobId })
                .innerJoin(PackagesAnalyzerRunsTable, { AnalyzerRunsTable.id }, { analyzerRunId })
                .innerJoin(PackagesTable, { PackagesAnalyzerRunsTable.packageId }, { PackagesTable.id })
                .innerJoin(IdentifiersTable, { PackagesTable.identifierId }, { IdentifiersTable.id })
                .innerJoin(RepositoriesTable, { OrtRunsTable.repositoryId }, { RepositoriesTable.id })
                .innerJoin(ProductsTable, { RepositoriesTable.productId }, { ProductsTable.id })

            when {
                identifier != null -> findByIdentifier(identifier, query, hierarchyFilter)
                else -> findByPurl(checkNotNull(purl), query, hierarchyFilter)
            }
        }
    }

    /**
     * Search for ORT runs containing the given vulnerability external ID, with optional scoping.
     *
     * @param externalId The vulnerability external ID regex to search for (e.g., CVE-2021-44228).
     * @param returnPurl When true, return the effective PURL; when false, return the package identifier.
     * @param userId The user ID performing the search.
     * @param scope Optional scope to limit the search. Can be an [OrganizationId], [ProductId], or [RepositoryId].
     *              If null, performs a global search (requires superuser permission).
     */
    suspend fun findOrtRunsByVulnerability(
        externalId: String,
        userId: String,
        scope: HierarchyId? = null,
        returnPurl: Boolean = false
    ): List<RunWithVulnerability> {
        val hierarchyFilter = authorizationService.filterHierarchyIds(
            userId = userId,
            repositoryPermissions = setOf(RepositoryPermission.READ),
            containedIn = scope
        )

        return db.blockingQuery {
            val query = createVulnerabilityBaseQuery()

            if (returnPurl) {
                findVulnerabilitiesWithPurl(externalId, query, hierarchyFilter)
            } else {
                findVulnerabilitiesWithIdentifier(externalId, query, hierarchyFilter)
            }
        }
    }
}

/**
 * Find runs by ORT package identifier without curation resolution (for performance reasons).
 */
private fun findByIdentifier(
    identifier: String,
    query: Join,
    hierarchyFilter: HierarchyFilter
): List<RunWithPackage> {
    val concatenatedIdentifier = concat(
        IdentifiersTable.type,
        stringLiteral(":"),
        IdentifiersTable.namespace,
        stringLiteral(":"),
        IdentifiersTable.name,
        stringLiteral(":"),
        IdentifiersTable.version
    )

    val identifierCondition = concatenatedIdentifier.applyIRegex(identifier)

    val whereClause = hierarchyFilter.apply(identifierCondition) { level, ids, filter ->
        generateHierarchyCondition(level, ids, filter)
    }

    return query.select(OrtRunsTable.columns + IdentifiersTable.columns).where(whereClause).map { row ->
        val ortRun = OrtRunDao.wrapRow(row).mapToModel()
        val packageId = Identifier(
            type = row[IdentifiersTable.type],
            namespace = row[IdentifiersTable.namespace],
            name = row[IdentifiersTable.name],
            version = row[IdentifiersTable.version]
        )

        RunWithPackage(
            organizationId = ortRun.organizationId,
            productId = ortRun.productId,
            repositoryId = ortRun.repositoryId,
            ortRunId = ortRun.id,
            ortRunIndex = ortRun.index,
            revision = ortRun.revision,
            createdAt = ortRun.createdAt,
            packageId = packageId,
            purl = null
        )
    }
}

/**
 * Find runs by PURL using curated PURL resolution.
 */
private fun findByPurl(
    purl: String,
    query: Join,
    hierarchyFilter: HierarchyFilter
): List<RunWithPackage> {
    val effectivePurl = createEffectivePurlExpression()
    val purlCondition = effectivePurl.applyIRegex(purl)

    val whereClause = hierarchyFilter.apply(purlCondition) { level, ids, filter ->
        generateHierarchyCondition(level, ids, filter)
    }

    return query.select(OrtRunsTable.columns + effectivePurl).where(whereClause).map { row ->
        val ortRun = OrtRunDao.wrapRow(row).mapToModel()

        RunWithPackage(
            organizationId = ortRun.organizationId,
            productId = ortRun.productId,
            repositoryId = ortRun.repositoryId,
            ortRunId = ortRun.id,
            ortRunIndex = ortRun.index,
            revision = ortRun.revision,
            createdAt = ortRun.createdAt,
            packageId = null,
            purl = row[effectivePurl]
        )
    }
}

private fun createVulnerabilityBaseQuery() = OrtRunsTable
    .innerJoin(AdvisorJobsTable, { OrtRunsTable.id }, { ortRunId })
    .innerJoin(AdvisorRunsTable, { AdvisorJobsTable.id }, { advisorJobId })
    .innerJoin(AdvisorRunsIdentifiersTable, { AdvisorRunsTable.id }, { advisorRunId })
    .innerJoin(IdentifiersTable, { AdvisorRunsIdentifiersTable.identifierId }, { IdentifiersTable.id })
    .innerJoin(AdvisorResultsTable, { AdvisorRunsIdentifiersTable.id }, { advisorRunIdentifierId })
    .innerJoin(AdvisorResultsVulnerabilitiesTable, { AdvisorResultsTable.id }, { advisorResultId })
    .innerJoin(
        VulnerabilitiesTable,
        { AdvisorResultsVulnerabilitiesTable.vulnerabilityId },
        { VulnerabilitiesTable.id }
    )
    .innerJoin(RepositoriesTable, { OrtRunsTable.repositoryId }, { RepositoriesTable.id })
    .innerJoin(ProductsTable, { RepositoriesTable.productId }, { ProductsTable.id })

/**
 * Search for vulnerabilities and return package identifiers.
 */
private fun findVulnerabilitiesWithIdentifier(
    externalId: String,
    query: Join,
    hierarchyFilter: HierarchyFilter
): List<RunWithVulnerability> {
    val vulnerabilityCondition = VulnerabilitiesTable.externalId.applyIRegex(externalId)

    val whereClause = hierarchyFilter.apply(vulnerabilityCondition) { level, ids, filter ->
        generateHierarchyCondition(level, ids, filter)
    }

    return query.select(
        OrtRunsTable.columns + IdentifiersTable.columns + VulnerabilitiesTable.externalId
    ).where(whereClause).map { row ->
        val ortRun = OrtRunDao.wrapRow(row).mapToModel()
        val packageId = Identifier(
            type = row[IdentifiersTable.type],
            namespace = row[IdentifiersTable.namespace],
            name = row[IdentifiersTable.name],
            version = row[IdentifiersTable.version]
        )
        RunWithVulnerability(
            organizationId = ortRun.organizationId,
            productId = ortRun.productId,
            repositoryId = ortRun.repositoryId,
            ortRunId = ortRun.id,
            ortRunIndex = ortRun.index,
            revision = ortRun.revision,
            createdAt = ortRun.createdAt,
            externalId = row[VulnerabilitiesTable.externalId],
            packageId = packageId,
            purl = null
        )
    }
}

/**
 * Search for vulnerabilities and return effective PURLs with curation resolution.
 */
private fun findVulnerabilitiesWithPurl(
    externalId: String,
    query: Join,
    hierarchyFilter: HierarchyFilter
): List<RunWithVulnerability> {
    // Join with PackagesTable to get the PURL
    val queryWithPackages = query
        .innerJoin(PackagesTable, { IdentifiersTable.id }, { identifierId })

    val effectivePurl = createEffectivePurlExpression()
    val vulnerabilityCondition = VulnerabilitiesTable.externalId.applyIRegex(externalId)

    val whereClause = hierarchyFilter.apply(vulnerabilityCondition) { level, ids, filter ->
        generateHierarchyCondition(level, ids, filter)
    }

    return queryWithPackages.select(
        OrtRunsTable.columns + VulnerabilitiesTable.externalId + effectivePurl
    ).where(whereClause).map { row ->
        val ortRun = OrtRunDao.wrapRow(row).mapToModel()
        RunWithVulnerability(
            organizationId = ortRun.organizationId,
            productId = ortRun.productId,
            repositoryId = ortRun.repositoryId,
            ortRunId = ortRun.id,
            ortRunIndex = ortRun.index,
            revision = ortRun.revision,
            createdAt = ortRun.createdAt,
            externalId = row[VulnerabilitiesTable.externalId],
            packageId = null,
            purl = row[effectivePurl]
        )
    }
}

/**
 * Create an expression for the effective PURL that respects curations. Return `COALESCE(curated_purl, original_purl)`
 * where the curated PURL is looked up from the resolved configuration for the current ORT run.
 */
private fun createEffectivePurlExpression(): CustomFunction<String> {
    val curatedPurlSubquery = PackageCurationDataTable
        .innerJoin(PackageCurationsTable)
        .innerJoin(ResolvedPackageCurationsTable)
        .innerJoin(ResolvedPackageCurationProvidersTable)
        .innerJoin(ResolvedConfigurationsTable)
        .select(PackageCurationDataTable.purl)
        .where {
            (ResolvedConfigurationsTable.ortRunId eq OrtRunsTable.id) and
                    (PackageCurationsTable.identifierId eq IdentifiersTable.id) and
                    (PackageCurationDataTable.purl.isNotNull())
        }
        .orderBy(ResolvedPackageCurationProvidersTable.rank)
        .orderBy(ResolvedPackageCurationsTable.rank)
        .limit(1)

    val curatedPurlExpression: Expression<String?> = wrapAsExpression(curatedPurlSubquery)

    return CustomFunction(
        "COALESCE",
        PackagesTable.purl.columnType,
        curatedPurlExpression,
        PackagesTable.purl
    )
}

/**
 * Generate a condition defined by a [HierarchyFilter] for the given [level] and [ids].
 */
private fun generateHierarchyCondition(
    level: HierarchyLevel,
    ids: List<CompoundHierarchyId>,
    filter: HierarchyFilter
): Op<Boolean> =
    when (level) {
        HierarchyLevel.REPOSITORY ->
            OrtRunsTable.repositoryId inList (
                ids.extractIds(HierarchyLevel.REPOSITORY) +
                    filter.nonTransitiveIncludes[HierarchyLevel.REPOSITORY].orEmpty()
                        .extractIds(HierarchyLevel.REPOSITORY)
            )

        HierarchyLevel.PRODUCT ->
            RepositoriesTable.productId inList ids.extractIds(HierarchyLevel.PRODUCT)

        HierarchyLevel.ORGANIZATION ->
            ProductsTable.organizationId inList ids.extractIds(HierarchyLevel.ORGANIZATION)

        HierarchyLevel.WILDCARD -> Op.FALSE
    }
