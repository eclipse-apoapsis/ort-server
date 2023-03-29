/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.tables.runs.analyzer

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

import org.ossreviewtoolkit.server.dao.tables.runs.shared.DeclaredLicenseDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifierDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifiersTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoTable
import org.ossreviewtoolkit.server.model.runs.Project

/**
 * A table to represent a software package as a project.
 */
object ProjectsTable : LongIdTable("projects") {
    val analyzerRunId = reference("analyzer_run_id", AnalyzerRunsTable)
    val identifierId = reference("identifier_id", IdentifiersTable)
    val vcsId = reference("vcs_id", VcsInfoTable)
    val vcsProcessedId = reference("vcs_processed_id", VcsInfoTable)

    val cpe = text("cpe").nullable()
    val definitionFilePath = text("definition_file_path")
    val homepageUrl = text("homepage_url")
}

class ProjectDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ProjectDao>(ProjectsTable) {
        fun findByProject(project: Project): ProjectDao? =
            find {
                ProjectsTable.cpe eq project.cpe and
                        (ProjectsTable.homepageUrl eq project.homepageUrl) and
                        (ProjectsTable.definitionFilePath eq project.definitionFilePath)
            }.singleOrNull {
                it.identifier.mapToModel() == project.identifier &&
                        it.authors == project.authors &&
                        it.declaredLicenses == project.declaredLicenses &&
                        it.vcs.mapToModel() == project.vcs &&
                        it.vcsProcessed.mapToModel() == project.vcsProcessed
            }
    }

    var analyzerRun by AnalyzerRunDao referencedOn ProjectsTable.analyzerRunId
    var identifier by IdentifierDao referencedOn ProjectsTable.identifierId
    var vcs by VcsInfoDao referencedOn ProjectsTable.vcsId
    var vcsProcessed by VcsInfoDao referencedOn ProjectsTable.vcsProcessedId

    var cpe by ProjectsTable.cpe
    var definitionFilePath by ProjectsTable.definitionFilePath
    var homepageUrl by ProjectsTable.homepageUrl

    var authors by AuthorDao via ProjectsAuthorsTable
    var declaredLicenses by DeclaredLicenseDao via ProjectsDeclaredLicensesTable
    val scopeNames by ProjectScopeDao referrersOn ProjectScopesTable.projectId

    fun mapToModel() = Project(
        identifier = identifier.mapToModel(),
        cpe = cpe,
        definitionFilePath = definitionFilePath,
        authors = authors.mapTo(mutableSetOf()) { it.name },
        declaredLicenses = declaredLicenses.mapTo(mutableSetOf()) { it.name },
        vcs = vcs.mapToModel(),
        vcsProcessed = vcsProcessed.mapToModel(),
        homepageUrl = homepageUrl,
        scopeNames = scopeNames.map(ProjectScopeDao::name).toSet()
    )
}
