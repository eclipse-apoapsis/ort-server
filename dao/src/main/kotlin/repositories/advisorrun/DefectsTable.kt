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

package org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun

import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Defect

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * A table to represent a software defect.
 */
object DefectsTable : LongIdTable("defects") {
    val externalId = text("external_id")
    val url = text("url")
    val title = text("title").nullable()
    val state = text("state").nullable()
    val severity = text("severity").nullable()
    val description = text("description").nullable()
    val creationTime = timestamp("creation_time").nullable()
    val modificationTime = timestamp("modification_time").nullable()
    val closingTime = timestamp("closing_time").nullable()
    val fixReleaseVersion = text("fix_release_version").nullable()
    val fixReleaseUrl = text("fix_release_url").nullable()
}

class DefectDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<DefectDao>(DefectsTable) {
        fun findByDefect(defect: Defect): DefectDao? =
            find {
                DefectsTable.externalId eq defect.externalId and
                        (DefectsTable.url eq defect.url) and
                        (DefectsTable.title eq defect.title) and
                        (DefectsTable.state eq defect.state) and
                        (DefectsTable.severity eq defect.severity) and
                        (DefectsTable.creationTime eq defect.creationTime) and
                        (DefectsTable.modificationTime eq defect.modificationTime) and
                        (DefectsTable.closingTime eq defect.closingTime) and
                        (DefectsTable.fixReleaseVersion eq defect.fixReleaseVersion) and
                        (DefectsTable.fixReleaseUrl eq defect.fixReleaseUrl)
            }.firstOrNull { dao ->
                dao.description == defect.description &&
                        dao.labels.associate { it.key to it.value } == defect.labels
            }

        fun getOrPut(defect: Defect): DefectDao =
            findByDefect(defect) ?: new {
                externalId = defect.externalId
                url = defect.url
                title = defect.title
                state = defect.state
                severity = defect.severity
                description = defect.description
                creationTime = defect.creationTime
                modificationTime = defect.modificationTime
                closingTime = defect.closingTime
                fixReleaseVersion = defect.fixReleaseVersion
                fixReleaseUrl = defect.fixReleaseUrl
            }.also {
                defect.labels.forEach { (key, value) ->
                    DefectLabelDao.new {
                        this.defect = it
                        this.key = key
                        this.value = value
                    }
                }
            }
    }

    var externalId by DefectsTable.externalId
    var url by DefectsTable.url
    var title by DefectsTable.title
    var state by DefectsTable.state
    var severity by DefectsTable.severity
    var description by DefectsTable.description
    var creationTime by DefectsTable.creationTime.transformToDatabasePrecision()
    var modificationTime by DefectsTable.modificationTime.transformToDatabasePrecision()
    var closingTime by DefectsTable.closingTime.transformToDatabasePrecision()
    var fixReleaseVersion by DefectsTable.fixReleaseVersion
    var fixReleaseUrl by DefectsTable.fixReleaseUrl

    val labels by DefectLabelDao referrersOn DefectLabelsTable.defectId

    fun mapToModel() = Defect(
        externalId = externalId,
        url = url,
        title = title,
        state = state,
        severity = severity,
        description = description,
        creationTime = creationTime,
        modificationTime = modificationTime,
        closingTime = closingTime,
        fixReleaseVersion = fixReleaseVersion,
        fixReleaseUrl = fixReleaseUrl,
        labels = labels.associate { it.key to it.value }
    )
}
