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
 * A table to store nested provenances.
 */
object NestedProvenancesTable : LongIdTable("nested_provenances") {
    val rootVcsId = reference("root_vcs_id", VcsInfoTable)

    val rootResolvedRevision = text("root_resolved_revision")
    val hasOnlyFixedRevisions = bool("has_only_fixed_revisions")

    // If specific VCS plugin configurations are used, store a canonical string representation of these configuration
    // options in this column. This ensures that results are only reused for scans with identical VCS plugin
    // configurations.
    val vcsPluginConfigs = text("vcs_plugin_configs").nullable()
}

class NestedProvenanceDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<NestedProvenanceDao>(NestedProvenancesTable)

    var rootVcs by VcsInfoDao referencedOn NestedProvenancesTable.rootVcsId

    var rootResolvedRevision by NestedProvenancesTable.rootResolvedRevision
    var hasOnlyFixedRevisions by NestedProvenancesTable.hasOnlyFixedRevisions
    var vcsPluginConfigs by NestedProvenancesTable.vcsPluginConfigs

    val packageProvenances by PackageProvenanceDao optionalReferrersOn PackageProvenancesTable.nestedProvenanceId
    val subRepositories by NestedProvenanceSubRepositoryDao referrersOn
            NestedProvenanceSubRepositoriesTable.nestedProvenanceId
}
