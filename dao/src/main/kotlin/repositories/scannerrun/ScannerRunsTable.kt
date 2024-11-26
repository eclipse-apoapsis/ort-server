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

package org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun

import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.ScannerJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.ScannerJobsTable
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenanceDao
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.EnvironmentDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.EnvironmentsTable
import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

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

    var startTime by ScannerRunsTable.startTime.transformToDatabasePrecision()
    var endTime by ScannerRunsTable.endTime.transformToDatabasePrecision()

    val config by ScannerConfigurationDao optionalBackReferencedOn ScannerConfigurationsTable.scannerRunId

    var packageProvenances by PackageProvenanceDao via ScannerRunsPackageProvenancesTable
    var scanResults by ScanResultDao via ScannerRunsScanResultsTable

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
        scanResults = emptySet(),
        scanners = emptyMap()
    )
}
