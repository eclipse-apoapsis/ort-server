/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.tables

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifierDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifiersTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactsTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoTable

object PackageProvenancesTable : LongIdTable("package_provenances") {
    val identifierId = reference("identifier_id", IdentifiersTable.id, ReferenceOption.CASCADE)
    val artifactId = reference("artifact_id", RemoteArtifactsTable.id, ReferenceOption.CASCADE).nullable()
    val vcsId = reference("vcs_id", VcsInfoTable.id, ReferenceOption.CASCADE).nullable()

    val resolvedRevision = text("resolved_revision").nullable()
    val clonedRevision = text("cloned_revision").nullable()
    val isFixedRevision = bool("is_fixed_revision").nullable()
    val errorMessage = text("error_message").nullable()
}

class PackageProvenanceDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PackageProvenanceDao>(PackageProvenancesTable)

    var identifier by IdentifierDao referencedOn PackageProvenancesTable.identifierId
    var artifact by RemoteArtifactDao optionalReferencedOn PackageProvenancesTable.artifactId
    var vcs by VcsInfoDao optionalReferencedOn PackageProvenancesTable.vcsId
    var resolvedRevision by PackageProvenancesTable.resolvedRevision
    var isFixedRevision by PackageProvenancesTable.isFixedRevision
    var clonedRevision by PackageProvenancesTable.clonedRevision
    var errorMessage by PackageProvenancesTable.errorMessage
}
