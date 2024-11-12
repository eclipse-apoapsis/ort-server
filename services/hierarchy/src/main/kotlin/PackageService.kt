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

package org.eclipse.apoapsis.ortserver.services

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.tables.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.AnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.PackageDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.PackagesAnalyzerRunsTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.analyzer.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.utils.listCustomQuery
import org.eclipse.apoapsis.ortserver.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult

import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.stringLiteral

/**
 * A service to interact with packages.
 */
class PackageService(private val db: Database) {
    suspend fun listForOrtRunId(
        ortRunId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): ListQueryResult<Package> = db.dbQuery {
        PackageDao.listCustomQuery(parameters, ResultRow::toPackage) {
            PackagesTable
                .innerJoin(PackagesAnalyzerRunsTable)
                .innerJoin(AnalyzerRunsTable)
                .innerJoin(AnalyzerJobsTable)
                .select(PackagesTable.columns)
                .where { AnalyzerJobsTable.ortRunId eq ortRunId }
        }
    }

    suspend fun countForOrtRunId(ortRunId: Long): Long = db.dbQuery {
        PackagesTable
            .innerJoin(PackagesAnalyzerRunsTable)
            .innerJoin(AnalyzerRunsTable)
            .innerJoin(AnalyzerJobsTable)
            .select(PackagesTable.id)
            .where { AnalyzerJobsTable.ortRunId eq ortRunId }
            .count()
    }

    suspend fun countEcosystemsForOrtRun(ortRunId: Long): List<EcosystemStats> =
        db.dbQuery {
            val countAlias = Count(stringLiteral("*"))
            PackagesTable
                .innerJoin(IdentifiersTable)
                .innerJoin(PackagesAnalyzerRunsTable)
                .innerJoin(AnalyzerRunsTable)
                .innerJoin(AnalyzerJobsTable)
                .select(IdentifiersTable.type, countAlias)
                .where { AnalyzerJobsTable.ortRunId eq ortRunId }
                .groupBy(IdentifiersTable.type)
                .map { row ->
                    EcosystemStats(
                        row[IdentifiersTable.type],
                        row[countAlias]
                    )
                }
        }
}

private fun ResultRow.toPackage(): Package = PackageDao.wrapRow(this).mapToModel()
