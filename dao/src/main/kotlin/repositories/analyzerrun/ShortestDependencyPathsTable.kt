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

package org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun

import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.ShortestDependencyPath

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.selectAll

/**
 * A table to store the shortest dependency path for a package.
 */
object ShortestDependencyPathsTable : LongIdTable("shortest_dependency_paths") {
    val packageId = reference("package_id", PackagesTable)
    val analyzerRunId = reference("analyzer_run_id", AnalyzerRunsTable)
    val projectId = reference("project_id", ProjectsTable)

    val scope = text("scope")
    val path = jsonb<List<Identifier>>("path")

    /**
     * Get the [ShortestDependencyPath]s for the given [ortRunId]. The key of the result are the [Identifier]s of the
     * packages, associated with the list of [ShortestDependencyPath]s.
     */
    fun getForOrtRunId(ortRunId: Long): Map<Identifier, List<ShortestDependencyPath>> {
        val projectIdentifiersTable = IdentifiersTable.alias("project_identifiers")

        val analyzerRunId = OrtRunsTable.getAnalyzerRunIdById(ortRunId)

        return innerJoin(PackagesTable)
            .innerJoin(ProjectsTable)
            .join(
                IdentifiersTable,
                JoinType.LEFT,
                PackagesTable.identifierId,
                IdentifiersTable.id
            )
            .join(
                projectIdentifiersTable,
                JoinType.LEFT,
                ProjectsTable.identifierId,
                projectIdentifiersTable[IdentifiersTable.id]
            )
            .selectAll()
            .where { ShortestDependencyPathsTable.analyzerRunId eq analyzerRunId }
            .map { resultRow ->
                val packageIdentifier = Identifier(
                    type = resultRow[IdentifiersTable.type],
                    namespace = resultRow[IdentifiersTable.namespace],
                    name = resultRow[IdentifiersTable.name],
                    version = resultRow[IdentifiersTable.version]
                )

                val projectIdentifier = Identifier(
                    type = resultRow[projectIdentifiersTable[IdentifiersTable.type]],
                    namespace = resultRow[projectIdentifiersTable[IdentifiersTable.namespace]],
                    name = resultRow[projectIdentifiersTable[IdentifiersTable.name]],
                    version = resultRow[projectIdentifiersTable[IdentifiersTable.version]]
                )

                packageIdentifier to ShortestDependencyPath(
                    projectIdentifier = projectIdentifier,
                    scope = resultRow[scope],
                    path = resultRow[path]
                )
            }.groupBy { it.first }
            .mapValues { (_, list) -> list.map { it.second } }
            .toMap()
    }
}

class ShortestDependencyPathDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ShortestDependencyPathDao>(ShortestDependencyPathsTable)

    var pkg by PackageDao referencedOn ShortestDependencyPathsTable.packageId
    var analyzerRun by AnalyzerRunDao referencedOn ShortestDependencyPathsTable.analyzerRunId
    var project by ProjectDao referencedOn ShortestDependencyPathsTable.projectId

    var scope by ShortestDependencyPathsTable.scope
    var path by ShortestDependencyPathsTable.path

    fun mapToModel() = ShortestDependencyPath(
        projectIdentifier = project.identifier.mapToModel(),
        scope = scope,
        path = path
    )
}
