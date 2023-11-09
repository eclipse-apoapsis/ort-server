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

package org.ossreviewtoolkit.server.dao.tables.runs.scanner

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

import org.ossreviewtoolkit.server.dao.tables.ScannerJobDao
import org.ossreviewtoolkit.server.dao.tables.ScannerJobsTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentsTable
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun

/**
 * A table to represent a summary of a scanner run.
 */
object ScannerRunsTable : LongIdTable("scanner_runs") {
    val scannerJobId = reference("scanner_job_id", ScannerJobsTable)
    val environmentId = reference("environment_id", EnvironmentsTable).nullable()

    val startTime = timestamp("start_time").nullable()
    val endTime = timestamp("end_time").nullable()
}

class ScannerRunDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScannerRunDao>(ScannerRunsTable)

    var scannerJob by ScannerJobDao referencedOn ScannerRunsTable.scannerJobId
    var environment by EnvironmentDao optionalReferencedOn ScannerRunsTable.environmentId

    var startTime by ScannerRunsTable.startTime.transform({ it?.toDatabasePrecision() }, { it })
    var endTime by ScannerRunsTable.endTime.transform({ it?.toDatabasePrecision() }, { it })

    val config by ScannerConfigurationDao optionalBackReferencedOn ScannerConfigurationsTable.scannerRunId

    fun mapToModel() = ScannerRun(
        id = id.value,
        scannerJobId = scannerJob.id.value,
        startTime = startTime,
        endTime = endTime,
        environment = environment?.mapToModel(),
        config = config?.mapToModel(),
        // TODO: Construct the provenance and scanResults sets as soon as there is a relation between Identifier,
        //       ScanResult and PackageProvenance.
        provenances = emptySet(),
        scanResults = emptySet()
    )
}
