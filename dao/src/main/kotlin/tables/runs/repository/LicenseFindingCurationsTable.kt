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

package org.ossreviewtoolkit.server.dao.tables.runs.repository

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

import org.ossreviewtoolkit.server.model.runs.repository.LicenseFindingCuration

/**
 * A table to represent a license finding curation, which is part of a
 * [PackageConfiguration][PackageConfigurationsTable] and [RepositoryConfiguration][RepositoryConfigurationsTable].
 */
object LicenseFindingCurationsTable : LongIdTable("license_finding_curations") {
    val path = text("path")
    val startLines = text("start_lines").nullable()
    val lineCount = integer("line_count").nullable()
    val detectedLicense = text("detected_license").nullable()
    val concludedLicense = text("concluded_license")
    val reason = text("reason")
    val comment = text("comment")
}

class LicenseFindingCurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<LicenseFindingCurationDao>(LicenseFindingCurationsTable) {
        fun findByLicenseFindingCuration(licenseFindingCuration: LicenseFindingCuration): LicenseFindingCurationDao? =
            find {
                with(LicenseFindingCurationsTable) {
                    path eq licenseFindingCuration.path and
                            (startLines eq licenseFindingCuration.startLines.joinToString(",")) and
                            (lineCount eq licenseFindingCuration.lineCount) and
                            (detectedLicense eq licenseFindingCuration.detectedLicense) and
                            (concludedLicense eq licenseFindingCuration.concludedLicense) and
                            (reason eq licenseFindingCuration.reason) and
                            (comment eq licenseFindingCuration.comment)
                }
            }.singleOrNull()

        fun getOrPut(licenseFindingCuration: LicenseFindingCuration): LicenseFindingCurationDao =
            findByLicenseFindingCuration(licenseFindingCuration) ?: new {
                path = licenseFindingCuration.path
                startLines = licenseFindingCuration.startLines
                lineCount = licenseFindingCuration.lineCount
                detectedLicense = licenseFindingCuration.detectedLicense
                concludedLicense = licenseFindingCuration.concludedLicense
                reason = licenseFindingCuration.reason
                comment = licenseFindingCuration.comment
            }
    }

    var path by LicenseFindingCurationsTable.path
    var startLines: List<Int>? by LicenseFindingCurationsTable.startLines
        .transform({ it?.joinToString(",") }, { it?.split(",")?.map(String::toInt) })
    var lineCount by LicenseFindingCurationsTable.lineCount
    var detectedLicense by LicenseFindingCurationsTable.detectedLicense
    var concludedLicense by LicenseFindingCurationsTable.concludedLicense
    var reason by LicenseFindingCurationsTable.reason
    var comment by LicenseFindingCurationsTable.comment

    fun mapToModel() = LicenseFindingCuration(
        path = path,
        startLines = startLines.orEmpty(),
        lineCount = lineCount,
        detectedLicense = detectedLicense,
        concludedLicense = concludedLicense,
        reason = reason,
        comment = comment
    )
}
