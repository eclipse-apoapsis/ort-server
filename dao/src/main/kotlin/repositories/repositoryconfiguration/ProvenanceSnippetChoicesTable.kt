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

package org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration

import org.eclipse.apoapsis.ortserver.dao.mapAndDeduplicate
import org.eclipse.apoapsis.ortserver.model.runs.repository.ProvenanceSnippetChoices
import org.eclipse.apoapsis.ortserver.model.runs.repository.snippet.Provenance

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to store snippet choices, which are part of a
 * [RepositoryConfiguration][RepositoryConfigurationsTable].
 */
object ProvenanceSnippetChoicesTable : LongIdTable("provenance_snippet_choices") {
    val provenance = text("provenance")
}

class SnippetChoicesDao(id: EntityID<Long>) : LongEntity(id) {
    var provenance by ProvenanceSnippetChoicesTable.provenance
    var choices by ChoicesDao via ProvenanceSnippetChoicesChoicesTable

    companion object : LongEntityClass<SnippetChoicesDao>(ProvenanceSnippetChoicesTable) {
        fun findByProvenanceSnippetChoices(provenanceSnippetChoices: ProvenanceSnippetChoices): SnippetChoicesDao? =
            find {
                with(ProvenanceSnippetChoicesTable) {
                    provenance eq provenanceSnippetChoices.provenance.url
                }
            }.firstOrNull {
                it.choices.map(ChoicesDao::mapToModel) == provenanceSnippetChoices.choices
            }

        fun getOrPut(provenanceSnippetChoices: ProvenanceSnippetChoices): SnippetChoicesDao =
            findByProvenanceSnippetChoices(provenanceSnippetChoices) ?: SnippetChoicesDao.new {
                provenance = provenanceSnippetChoices.provenance.url
                choices = mapAndDeduplicate(provenanceSnippetChoices.choices, ChoicesDao::getOrPut)
            }
    }

    fun mapToModel() = ProvenanceSnippetChoices(
        provenance = Provenance(provenance),
        choices = choices.map(ChoicesDao::mapToModel)
    )
}
