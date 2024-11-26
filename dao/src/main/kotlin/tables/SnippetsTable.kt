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

import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactsTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.dao.utils.jsonb
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ArtifactProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.RepositoryProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.Snippet
import org.eclipse.apoapsis.ortserver.model.runs.scanner.TextLocation

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent a snippet.
 */
object SnippetsTable : LongIdTable("snippets") {
    val purl = text("purl")
    val artifactId = reference("artifact_id", RemoteArtifactsTable).nullable()
    val vcsId = reference("vcs_id", VcsInfoTable).nullable()
    val path = text("path")
    val startLine = integer("start_line")
    val endLine = integer("end_line")
    val license = text("license")
    val score = float("score")
    val additionalData = jsonb<AdditionalSnippetData>("additional_data").nullable()
}

class SnippetDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SnippetDao>(SnippetsTable) {
        fun findBySnippet(snippet: Snippet): SnippetDao? =
            find {
                SnippetsTable.purl eq snippet.purl and
                        (SnippetsTable.path eq snippet.location.path) and
                        (SnippetsTable.startLine eq snippet.location.startLine) and
                        (SnippetsTable.endLine eq snippet.location.endLine) and
                        (SnippetsTable.license eq snippet.spdxLicense) and
                        (SnippetsTable.score eq snippet.score)
            }.firstOrNull {
                it.mapToModel().provenance == snippet.provenance && it.additionalData?.data == snippet.additionalData
            }

        fun put(snippet: Snippet): SnippetDao =
            new {
                this.purl = snippet.purl
                this.path = snippet.location.path
                this.startLine = snippet.location.startLine
                this.endLine = snippet.location.endLine
                this.score = snippet.score
                this.license = snippet.spdxLicense
                this.additionalData = AdditionalSnippetData(snippet.additionalData)
                this.vcs = (snippet.provenance as? RepositoryProvenance)?.vcsInfo?.let { VcsInfoDao.getOrPut(it) }
                this.artifact = (snippet.provenance as? ArtifactProvenance)?.sourceArtifact?.let {
                    RemoteArtifactDao.getOrPut(it)
                }
            }
    }

    var artifact by RemoteArtifactDao optionalReferencedOn SnippetsTable.artifactId
    var vcs by VcsInfoDao optionalReferencedOn SnippetsTable.vcsId

    var purl by SnippetsTable.purl
    var path by SnippetsTable.path
    var startLine by SnippetsTable.startLine
    var endLine by SnippetsTable.endLine
    var license by SnippetsTable.license
    var score by SnippetsTable.score
    var additionalData by SnippetsTable.additionalData

    fun mapToModel(): Snippet {
        val provenance = artifact?.mapToModel()?.let { ArtifactProvenance(it) }
            ?: vcs?.mapToModel()?.let { RepositoryProvenance(it, it.revision) }

        checkNotNull(provenance)

        return Snippet(
            purl = purl,
            provenance = provenance,
            location = TextLocation(path, startLine, endLine),
            score = score,
            spdxLicense = license,
            additionalData = additionalData?.data.orEmpty()
        )
    }
}

@Serializable
data class AdditionalSnippetData(
    var data: Map<String, String>
)
