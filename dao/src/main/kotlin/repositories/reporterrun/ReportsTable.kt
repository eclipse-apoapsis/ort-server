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

package org.eclipse.apoapsis.ortserver.dao.repositories.reporterrun

import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * A table to represent a report.
 */
object ReportsTable : LongIdTable("reports") {
    val filename = text("report_filename")
    val downloadLink = text("download_link")
    val downloadTokenExpiryDate = timestamp("download_token_expiry_date")
}

class ReportDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ReportDao>(ReportsTable)

    var filename by ReportsTable.filename
    var downloadLink by ReportsTable.downloadLink
    var downloadTokenExpiryDate by ReportsTable.downloadTokenExpiryDate

    fun mapToModel() = Report(
        filename = filename,
        downloadLink = downloadLink,
        downloadTokenExpiryDate = downloadTokenExpiryDate
    )
}
