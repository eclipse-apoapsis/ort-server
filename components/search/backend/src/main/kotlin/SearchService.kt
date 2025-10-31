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
import org.eclipse.apoapsis.ortserver.dao.utils.applyRegex

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.concat
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.stringLiteral

class SearchService(private val db: Database) {
    /**
     * Search for Analyzer runs containing the given package identifier, with optional scoping.
     * Throws IllegalArgumentException for invalid scoping hierarchy.
     */
    @Suppress("LongMethod")
    fun findOrtRunsByPackage(
        identifier: String,
        organizationId: Long? = null,
        productId: Long? = null,
        repositoryId: Long? = null
    ): List<RunWithPackage> = db.blockingQuery {
        validateScope(organizationId, productId, repositoryId)

        // Build base query
        var query = OrtRunsTable
            .innerJoin(AnalyzerJobsTable, { OrtRunsTable.id }, { ortRunId })
            .innerJoin(AnalyzerRunsTable, { AnalyzerJobsTable.id }, { analyzerJobId })
            .innerJoin(PackagesAnalyzerRunsTable, { AnalyzerRunsTable.id }, { PackagesAnalyzerRunsTable.analyzerRunId })
            .innerJoin(PackagesTable, { PackagesAnalyzerRunsTable.packageId }, { PackagesTable.id })
            .innerJoin(IdentifiersTable, { PackagesTable.identifierId }, { IdentifiersTable.id })

        // Convert Identifier to a concatenated string format for ILike comparison
        val concatenatedIdentifier = concat(
            IdentifiersTable.type,
            stringLiteral(":"),
            IdentifiersTable.namespace,
            stringLiteral(":"),
            IdentifiersTable.name,
            stringLiteral(":"),
            IdentifiersTable.version
        )

        val conditions = mutableListOf(concatenatedIdentifier.applyRegex(identifier))

        val scopeRequested = organizationId != null || productId != null || repositoryId != null
        if (scopeRequested) {
            query = query
                .innerJoin(RepositoriesTable, { OrtRunsTable.repositoryId }, { RepositoriesTable.id })
                .innerJoin(ProductsTable, { RepositoriesTable.productId }, { ProductsTable.id })
        }

        organizationId?.let { conditions += ProductsTable.organizationId eq it }
        productId?.let { conditions += RepositoriesTable.productId eq it }
        repositoryId?.let { conditions += OrtRunsTable.repositoryId eq it }

        val whereClause = conditions.reduce { acc, expression -> acc and expression }

        val resultRows = query.select(OrtRunsTable.columns + IdentifiersTable.columns).where { whereClause }
        resultRows.map { row ->
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
                revision = ortRun.revision,
                createdAt = ortRun.createdAt,
                packageId = packageId
            )
        }
    }

    private fun validateScope(organizationId: Long?, productId: Long?, repositoryId: Long?) {
        require(!(repositoryId != null && (productId == null || organizationId == null))) {
            "If repositoryId is provided, productId and organizationId must also be provided."
        }
        require(organizationId != null || productId == null) {
            "If productId is provided, organizationId must also be provided."
        }
    }
}
