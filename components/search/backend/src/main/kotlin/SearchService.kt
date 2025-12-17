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

import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.components.search.apimodel.RunWithPackage
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.product.ProductsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.RepositoriesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.utils.apply
import org.eclipse.apoapsis.ortserver.dao.utils.applyRegex
import org.eclipse.apoapsis.ortserver.dao.utils.extractIds
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.util.HierarchyFilter

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Join
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.concat
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.stringLiteral

class SearchService(
    private val db: Database,
    private val authorizationService: AuthorizationService
) {
    /**
     * Search for Analyzer runs containing the given package identifier, with optional scoping.
     *
     * @param identifier The package identifier regex to search for.
     * @param userId The user ID performing the search.
     * @param scope Optional scope to limit the search. Can be an [OrganizationId], [ProductId], or [RepositoryId].
     *              If null, performs a global search (requires superuser permission).
     */
    suspend fun findOrtRunsByPackage(
        identifier: String,
        userId: String,
        scope: HierarchyId? = null
    ): List<RunWithPackage> {
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

            findByIdentifier(query, identifier, hierarchyFilter)
        }
    }
}

/**
 * Find runs by ORT package identifier without curation resolution (for performance reasons).
 */
private fun findByIdentifier(
    query: Join,
    identifier: String,
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

    val identifierCondition = concatenatedIdentifier.applyRegex(identifier)

    val whereClause = hierarchyFilter.apply(identifierCondition) { level, ids, filter ->
        generateHierarchyCondition(level, ids, filter)
    }

    return query.select(OrtRunsTable.columns + IdentifiersTable.columns).where(whereClause).map { row ->
        val ortRun = OrtRunDao.wrapRow(row).mapToModel()
        val packageId = listOf(
            row[IdentifiersTable.type],
            row[IdentifiersTable.namespace],
            row[IdentifiersTable.name],
            row[IdentifiersTable.version]
        ).joinToString(":")

        RunWithPackage(
            organizationId = ortRun.organizationId,
            productId = ortRun.productId,
            repositoryId = ortRun.repositoryId,
            ortRunId = ortRun.id,
            ortRunIndex = ortRun.index,
            revision = ortRun.revision,
            createdAt = ortRun.createdAt,
            packageId = packageId
        )
    }
}

/**
 * Generate a condition defined by a [HierarchyFilter] for the given [level] and [ids].
 */
private fun SqlExpressionBuilder.generateHierarchyCondition(
    level: Int,
    ids: List<CompoundHierarchyId>,
    filter: HierarchyFilter
): Op<Boolean> =
    when (level) {
        CompoundHierarchyId.REPOSITORY_LEVEL ->
            OrtRunsTable.repositoryId inList (
                ids.extractIds(CompoundHierarchyId.REPOSITORY_LEVEL) +
                    filter.nonTransitiveIncludes[CompoundHierarchyId.REPOSITORY_LEVEL].orEmpty()
                        .extractIds(CompoundHierarchyId.REPOSITORY_LEVEL)
            )

        CompoundHierarchyId.PRODUCT_LEVEL ->
            RepositoriesTable.productId inList ids.extractIds(CompoundHierarchyId.PRODUCT_LEVEL)

        CompoundHierarchyId.ORGANIZATION_LEVEL ->
            ProductsTable.organizationId inList ids.extractIds(CompoundHierarchyId.ORGANIZATION_LEVEL)

        else -> Op.FALSE
    }
