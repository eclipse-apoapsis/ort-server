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

package org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration

import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackagesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationDao
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.PackageCurationsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCuration

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * A table to store curated packages, linking [OrtRunsTable], [PackagesTable], and [ResolvedPackageCurationsTable].
 */
object CuratedPackagesTable : LongIdTable("curated_packages") {
    val ortRunId = reference("ort_run_id", OrtRunsTable)
    val packageId = reference("package_id", PackagesTable)
    val resolvedPackageCurationId = reference("resolved_package_curation_id", ResolvedPackageCurationsTable)

    /**
     * Get the [PackageCuration]s for the given [ortRunId], grouped by the package [Identifier] they apply to. Each
     * curation is paired with the name of the provider that supplied it.
     */
    fun getForOrtRunId(ortRunId: Long): Map<Identifier, List<Pair<String, PackageCuration>>> {
        val rows = innerJoin(PackagesTable)
            .innerJoin(ResolvedPackageCurationsTable)
            .innerJoin(ResolvedPackageCurationProvidersTable)
            .innerJoin(PackageCurationProviderConfigsTable)
            .join(IdentifiersTable, JoinType.INNER, PackagesTable.identifierId, IdentifiersTable.id)
            .selectAll()
            .where { CuratedPackagesTable.ortRunId eq ortRunId }
            .map { row ->
                Triple(
                    Identifier(
                        type = row[IdentifiersTable.type],
                        namespace = row[IdentifiersTable.namespace],
                        name = row[IdentifiersTable.name],
                        version = row[IdentifiersTable.version]
                    ),
                    row[PackageCurationProviderConfigsTable.name],
                    row[ResolvedPackageCurationsTable.packageCurationId].value
                )
            }

        if (rows.isEmpty()) return emptyMap()

        val curationIds = rows.map { it.third }.toSet()
        val curationsById = PackageCurationDao.find { PackageCurationsTable.id inList curationIds }
            .associate { it.id.value to it.mapToModel() }

        return rows
            .map { (identifier, providerName, curationId) ->
                identifier to (providerName to curationsById.getValue(curationId))
            }
            .groupBy({ it.first }, { it.second })
    }
}
