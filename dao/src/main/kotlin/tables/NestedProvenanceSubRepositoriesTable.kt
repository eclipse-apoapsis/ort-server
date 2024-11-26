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

package org.eclipse.apoapsis.ortserver.dao.tables

import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to store the sub-repositories of [nested provenances][NestedProvenancesTable].
 */
object NestedProvenanceSubRepositoriesTable : LongIdTable("nested_provenance_sub_repositories") {
    val nestedProvenanceId = reference("nested_provenance_id", NestedProvenancesTable)
    val vcsId = reference("vcs_id", VcsInfoTable)

    val resolvedRevision = text("resolved_revision")
    var path = text("path")
}

class NestedProvenanceSubRepositoryDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<NestedProvenanceSubRepositoryDao>(NestedProvenanceSubRepositoriesTable)

    var nestedProvenance by NestedProvenanceDao referencedOn NestedProvenanceSubRepositoriesTable.nestedProvenanceId
    var vcs by VcsInfoDao referencedOn NestedProvenanceSubRepositoriesTable.vcsId

    var resolvedRevision by NestedProvenanceSubRepositoriesTable.resolvedRevision
    var path by NestedProvenanceSubRepositoriesTable.path
}
