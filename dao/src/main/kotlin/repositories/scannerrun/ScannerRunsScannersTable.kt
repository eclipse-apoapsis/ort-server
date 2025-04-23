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

import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.model.runs.Identifier

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to track which packages have been scanned by which scanners in an ORT run.
 */
object ScannerRunsScannersTable : LongIdTable("scanner_runs_scanners") {
    val scannerRunId = reference("scanner_run_id", ScannerRunsTable)
    val identifierId = reference("identifier_id", IdentifiersTable)

    val scannerName = text("scanner_name")
}

class ScannerRunsScannersDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScannerRunsScannersDao>(ScannerRunsScannersTable) {
        /**
         * Add an entry to record that in the given [run] the package with the given [identifier] was scanned by the
         * scanner with th given [scannerName].
         */
        fun addScanner(run: ScannerRunDao, identifier: Identifier, scannerName: String) {
            new {
                scannerRun = run
                this.identifier = IdentifierDao.getOrPut(identifier)
                this.scannerName = scannerName
            }
        }
    }

    var scannerRun by ScannerRunDao referencedOn ScannerRunsScannersTable.scannerRunId
    var identifier by IdentifierDao referencedOn ScannerRunsScannersTable.identifierId

    var scannerName by ScannerRunsScannersTable.scannerName
}
