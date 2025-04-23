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

import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.PackageDao
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsPackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ArtifactProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.Provenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.RepositoryProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.UnknownProvenance

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or

object PackageProvenancesTable : LongIdTable("package_provenances") {
    val identifierId = reference("identifier_id", IdentifiersTable)
    val artifactId = reference("artifact_id", RemoteArtifactsTable).nullable()
    val vcsId = reference("vcs_id", VcsInfoTable).nullable()
    val nestedProvenanceId = reference("nested_provenance_id", NestedProvenancesTable).nullable()

    val resolvedRevision = text("resolved_revision").nullable()
    val clonedRevision = text("cloned_revision").nullable()
    val isFixedRevision = bool("is_fixed_revision").nullable()
    val errorMessage = text("error_message").nullable()
}

class PackageProvenanceDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PackageProvenanceDao>(PackageProvenancesTable) {
        /**
         * Return a matching package provenance for the provided [package][pkg] or null if no provenance is found.
         */
        fun findByPackage(pkg: PackageDao): PackageProvenanceDao? =
            // TODO: Make the source code origin configurable, currently the random first finding is used when multiple
            //       provenances are found for a package.
            find {
                (PackageProvenancesTable.identifierId eq pkg.identifier.id) and (
                        (PackageProvenancesTable.artifactId eq pkg.sourceArtifact.id) or
                                (PackageProvenancesTable.vcsId eq pkg.vcsProcessed.id)
                        )
            }.firstOrNull()
    }

    var identifier by IdentifierDao referencedOn PackageProvenancesTable.identifierId
    var artifact by RemoteArtifactDao optionalReferencedOn PackageProvenancesTable.artifactId
    var vcs by VcsInfoDao optionalReferencedOn PackageProvenancesTable.vcsId
    var nestedProvenance by NestedProvenanceDao optionalReferencedOn PackageProvenancesTable.nestedProvenanceId

    var resolvedRevision by PackageProvenancesTable.resolvedRevision
    var isFixedRevision by PackageProvenancesTable.isFixedRevision
    var clonedRevision by PackageProvenancesTable.clonedRevision
    var errorMessage by PackageProvenancesTable.errorMessage

    var scannerRuns by ScannerRunDao via ScannerRunsPackageProvenancesTable

    fun mapToModel(): Provenance {
        if (errorMessage != null) return UnknownProvenance

        return artifact?.mapToModel()?.let { ArtifactProvenance(it) }
            ?: vcs?.mapToModel()?.let { RepositoryProvenance(it, checkNotNull(resolvedRevision)) }
            ?: UnknownProvenance
    }
}
