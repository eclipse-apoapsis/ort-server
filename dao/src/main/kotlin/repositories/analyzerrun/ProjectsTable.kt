/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import org.eclipse.apoapsis.ortserver.dao.tables.shared.DeclaredLicenseDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.DeclaredLicensesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.dao.utils.ArrayAggColumnEquals
import org.eclipse.apoapsis.ortserver.dao.utils.ArrayAggNullableColumnEquals
import org.eclipse.apoapsis.ortserver.dao.utils.ArrayAggTwoColumnsEquals
import org.eclipse.apoapsis.ortserver.model.runs.Project

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.andHaving
import org.jetbrains.exposed.sql.andWhere

/**
 * A table to represent a software package as a project.
 */
object ProjectsTable : LongIdTable("projects") {
    val identifierId = reference("identifier_id", IdentifiersTable)
    val vcsId = reference("vcs_id", VcsInfoTable)
    val vcsProcessedId = reference("vcs_processed_id", VcsInfoTable)

    val cpe = text("cpe").nullable()
    val definitionFilePath = text("definition_file_path")
    val description = text("description")
    val homepageUrl = text("homepage_url")
}

class ProjectDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ProjectDao>(ProjectsTable) {
        fun findByProject(project: Project): ProjectDao? {
            val vcsProcessed = VcsInfoTable.alias("vcs_processed_info")
            val query = ProjectsTable
                .leftJoin(IdentifiersTable)
                .join(VcsInfoTable, JoinType.LEFT, onColumn = ProjectsTable.vcsId, otherColumn = VcsInfoTable.id)
                .join(vcsProcessed, JoinType.LEFT, ProjectsTable.vcsProcessedId, vcsProcessed[VcsInfoTable.id])
                .leftJoin(ProjectsAuthorsTable)
                .leftJoin(AuthorsTable)
                .leftJoin(ProjectsDeclaredLicensesTable)
                .leftJoin(DeclaredLicensesTable)
                .leftJoin(ProjectScopesTable)
                .leftJoin(ProcessedDeclaredLicensesTable)
                .leftJoin(ProcessedDeclaredLicensesMappedDeclaredLicensesTable)
                .leftJoin(MappedDeclaredLicensesTable)
                .leftJoin(ProcessedDeclaredLicensesUnmappedDeclaredLicensesTable)
                .leftJoin(UnmappedDeclaredLicensesTable)
                .select(ProjectsTable.id)
                .where { ProjectsTable.cpe eq project.cpe }
                .andWhere { ProjectsTable.definitionFilePath eq project.definitionFilePath }
                .andWhere { ProjectsTable.description eq project.description }
                .andWhere { ProjectsTable.homepageUrl eq project.homepageUrl }
                .andWhere { IdentifiersTable.type eq project.identifier.type }
                .andWhere { IdentifiersTable.namespace eq project.identifier.namespace }
                .andWhere { IdentifiersTable.name eq project.identifier.name }
                .andWhere { IdentifiersTable.version eq project.identifier.version }
                .andWhere { VcsInfoTable.type eq project.vcs.type.name }
                .andWhere { VcsInfoTable.url eq project.vcs.url }
                .andWhere { VcsInfoTable.revision eq project.vcs.revision }
                .andWhere { VcsInfoTable.path eq project.vcs.path }
                .andWhere { vcsProcessed[VcsInfoTable.type] eq project.vcsProcessed.type.name }
                .andWhere { vcsProcessed[VcsInfoTable.url] eq project.vcsProcessed.url }
                .andWhere { vcsProcessed[VcsInfoTable.revision] eq project.vcsProcessed.revision }
                .andWhere { vcsProcessed[VcsInfoTable.path] eq project.vcsProcessed.path }
                .groupBy(ProjectsTable.id, IdentifiersTable.id, VcsInfoTable.id, vcsProcessed[VcsInfoTable.id])
                .having { ArrayAggColumnEquals(AuthorsTable.name, project.authors) }
                .andHaving { ArrayAggColumnEquals(DeclaredLicensesTable.name, project.declaredLicenses) }
                .andHaving { ArrayAggColumnEquals(ProjectScopesTable.name, project.scopeNames) }
                .andHaving {
                    ArrayAggNullableColumnEquals(
                        ProcessedDeclaredLicensesTable.spdxExpression,
                        setOf(project.processedDeclaredLicense.spdxExpression.toString())
                    )
                }
                .andHaving {
                    ArrayAggColumnEquals(
                        UnmappedDeclaredLicensesTable.unmappedLicense,
                        project.processedDeclaredLicense.unmappedLicenses
                    )
                }
                .andHaving {
                    ArrayAggTwoColumnsEquals(
                        MappedDeclaredLicensesTable.declaredLicense,
                        MappedDeclaredLicensesTable.mappedLicense,
                        project.processedDeclaredLicense.mappedLicenses
                    )
                }

            val id = query.firstOrNull()?.let { it[ProjectsTable.id] } ?: return null

            return ProjectDao[id]
        }
    }

    var identifier by IdentifierDao referencedOn ProjectsTable.identifierId
    var vcs by VcsInfoDao referencedOn ProjectsTable.vcsId
    var vcsProcessed by VcsInfoDao referencedOn ProjectsTable.vcsProcessedId

    var cpe by ProjectsTable.cpe
    var definitionFilePath by ProjectsTable.definitionFilePath
    var description by ProjectsTable.description
    var homepageUrl by ProjectsTable.homepageUrl

    var authors by AuthorDao via ProjectsAuthorsTable
    var declaredLicenses by DeclaredLicenseDao via ProjectsDeclaredLicensesTable
    val scopeNames by ProjectScopeDao referrersOn ProjectScopesTable.projectId
    var analyzerRuns by AnalyzerRunDao via ProjectsAnalyzerRunsTable

    val processedDeclaredLicense by ProcessedDeclaredLicenseDao backReferencedOn
            ProcessedDeclaredLicensesTable.projectId

    fun mapToModel() = Project(
        identifier = identifier.mapToModel(),
        cpe = cpe,
        definitionFilePath = definitionFilePath,
        authors = authors.mapTo(mutableSetOf()) { it.name },
        declaredLicenses = declaredLicenses.mapTo(mutableSetOf()) { it.name },
        processedDeclaredLicense = processedDeclaredLicense.mapToModel(),
        vcs = vcs.mapToModel(),
        vcsProcessed = vcsProcessed.mapToModel(),
        description = description,
        homepageUrl = homepageUrl,
        scopeNames = scopeNames.mapTo(mutableSetOf(), ProjectScopeDao::name)
    )
}
