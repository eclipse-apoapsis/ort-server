/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.selectAll

/**
 * A table to store the results of processing declared licenses.
 */
object ProcessedDeclaredLicensesTable : LongIdTable("processed_declared_licenses") {
    val packageId = reference("package_id", PackagesTable).nullable()
    val projectId = reference("project_id", ProjectsTable).nullable()

    val spdxExpression = text("spdx_expression").nullable()

    /** Get the [ProcessedDeclaredLicense]s for the provided [packageIds]. */
    fun getByPackageIds(packageIds: Set<Long>): Map<Long, ProcessedDeclaredLicense?> =
        getByIds({ packageId inList packageIds }, { this[packageId]?.value })

    /** Get the [ProcessedDeclaredLicense]s for the provided [projectIds]. */
    fun getByProjectIds(projectIds: Set<Long>): Map<Long, ProcessedDeclaredLicense?> =
        getByIds({ projectId inList projectIds }, { this[projectId]?.value })

    /**
     * Get the [ProcessedDeclaredLicense]s matching the provided [whereCondition], associated by the provided
     * [idSelector].
     */
    @Suppress("UNCHECKED_CAST")
    private fun getByIds(
        whereCondition: SqlExpressionBuilder.() -> Op<Boolean>,
        idSelector: ResultRow.() -> Long?
    ): Map<Long, ProcessedDeclaredLicense?> =
        leftJoin(ProcessedDeclaredLicensesMappedDeclaredLicensesTable)
            .leftJoin(MappedDeclaredLicensesTable)
            .leftJoin(ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable)
            .leftJoin(UnmappedDeclaredLicensesTable)
            .selectAll()
            .where(whereCondition)
            .groupBy { it.idSelector() }
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
                    spdxExpression = resultRows[0][spdxExpression],
                    mappedLicenses = mappedLicenses,
                    unmappedLicenses = unmappedLicenses
                )
            } as Map<Long, ProcessedDeclaredLicense?>
}

class ProcessedDeclaredLicenseDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ProcessedDeclaredLicenseDao>(ProcessedDeclaredLicensesTable)

    var pkg by PackageDao optionalReferencedOn ProcessedDeclaredLicensesTable.packageId
    var project by ProjectDao optionalReferencedOn ProcessedDeclaredLicensesTable.projectId

    var spdxExpression by ProcessedDeclaredLicensesTable.spdxExpression

    val mappedLicenses by MappedDeclaredLicenseDao via ProcessedDeclaredLicensesMappedDeclaredLicensesTable
    val unmappedLicenses by UnmappedDeclaredLicenseDao via ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable

    fun mapToModel(): ProcessedDeclaredLicense = ProcessedDeclaredLicense(
        spdxExpression = spdxExpression,
        mappedLicenses = mappedLicenses.associate { it.declaredLicense to it.mappedLicense },
        unmappedLicenses = unmappedLicenses.mapTo(mutableSetOf()) { it.unmappedLicense }
    )
}
