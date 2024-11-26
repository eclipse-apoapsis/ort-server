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

import org.eclipse.apoapsis.ortserver.model.runs.repository.snippet.Choice
import org.eclipse.apoapsis.ortserver.model.runs.repository.snippet.Given
import org.eclipse.apoapsis.ortserver.model.runs.repository.snippet.SnippetChoice
import org.eclipse.apoapsis.ortserver.model.runs.repository.snippet.SnippetChoiceReason
import org.eclipse.apoapsis.ortserver.model.runs.scanner.TextLocation

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent a snippet choice, which is part of a
 * [PackageConfiguration][ProvenanceSnippetChoicesTable] and [RepositoryConfiguration][RepositoryConfigurationsTable].
 */
object SnippetChoicesTable : LongIdTable("snippet_choices") {
    val givenLocationStartLine = integer("given_location_start_line")
    val givenLocationEndLine = integer("given_location_end_line")
    val givenLocationPath = text("given_location_path")
    val choicePurl = text("choice_purl").nullable()
    val choiceReason = text("choice_reason")
    val choiceComment = text("choice_comment").nullable()
}

class ChoicesDao(id: EntityID<Long>) : LongEntity(id) {
    var givenLocationPath by SnippetChoicesTable.givenLocationPath
    var givenLocationStartLine by SnippetChoicesTable.givenLocationStartLine
    var givenLocationEndLine by SnippetChoicesTable.givenLocationEndLine
    var choicePurl by SnippetChoicesTable.choicePurl
    var choiceReason by SnippetChoicesTable.choiceReason
    var choiceComment by SnippetChoicesTable.choiceComment

    companion object : LongEntityClass<ChoicesDao>(SnippetChoicesTable) {
        fun findBySnippetChoice(snippetChoice: SnippetChoice): ChoicesDao? =
            find {
                with(SnippetChoicesTable) {
                    (givenLocationPath eq snippetChoice.given.sourceLocation.path) and
                            (givenLocationStartLine eq snippetChoice.given.sourceLocation.startLine) and
                            (givenLocationEndLine eq snippetChoice.given.sourceLocation.endLine) and
                            (choicePurl eq snippetChoice.choice.purl) and
                            (choiceReason eq snippetChoice.choice.reason.name) and
                            (choiceComment eq snippetChoice.choice.comment)
                }
            }.firstOrNull()

        fun getOrPut(snippetChoice: SnippetChoice): ChoicesDao =
            findBySnippetChoice(snippetChoice) ?: new {
                givenLocationPath = snippetChoice.given.sourceLocation.path
                givenLocationStartLine = snippetChoice.given.sourceLocation.startLine
                givenLocationEndLine = snippetChoice.given.sourceLocation.endLine
                choicePurl = snippetChoice.choice.purl
                choiceReason = snippetChoice.choice.reason.name
                choiceComment = snippetChoice.choice.comment
            }
    }

    fun mapToModel() = SnippetChoice(
        Given(
            TextLocation(givenLocationPath, givenLocationStartLine, givenLocationEndLine)
        ),
        Choice(
            choicePurl,
            SnippetChoiceReason.valueOf(choiceReason),
            choiceComment
        )
    )
}
