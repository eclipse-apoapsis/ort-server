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

import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.ShortestDependencyPath

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to store the shortest dependency path for a package.
 */
object ShortestDependencyPathsTable : LongIdTable("shortest_dependency_paths") {
    val packageId = reference("package_id", PackagesTable)
    val analyzerRunId = reference("analyzer_run_id", AnalyzerRunsTable)
    val projectId = reference("project_id", ProjectsTable)

    val scope = text("scope")
    val path = jsonb<List<Identifier>>("path")
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
