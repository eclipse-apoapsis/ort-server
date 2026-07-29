/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.queries.licensefindings

import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.ScannerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsPackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsTable
import org.eclipse.apoapsis.ortserver.dao.tables.LicenseFindingsTable
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultPackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanResultsTable
import org.eclipse.apoapsis.ortserver.dao.tables.ScanSummariesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable

import org.jetbrains.exposed.v1.core.Join
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq

/**
 * Build the common join for license finding queries.
 *
 * The join uses both scan result association tables to ensure that a result belongs to the matched package provenance
 * and scanner run. Callers must additionally restrict [ScannerJobsTable.ortRunId] to the requested ORT run.
 */
fun buildLicenseFindingsJoin(): Join =
    LicenseFindingsTable
        .innerJoin(ScanSummariesTable)
        .join(ScanResultsTable, JoinType.INNER, ScanSummariesTable.id, ScanResultsTable.scanSummaryId)
        .join(
            ScanResultPackageProvenancesTable,
            JoinType.INNER,
            ScanResultsTable.id,
            ScanResultPackageProvenancesTable.scanResultId
        )
        .join(
            PackageProvenancesTable,
            JoinType.INNER,
            ScanResultPackageProvenancesTable.packageProvenanceId,
            PackageProvenancesTable.id
        )
        .join(
            ScannerRunsPackageProvenancesTable,
            JoinType.INNER,
            PackageProvenancesTable.id,
            ScannerRunsPackageProvenancesTable.packageProvenanceId
        )
        .join(
            ScannerRunsTable,
            JoinType.INNER,
            ScannerRunsPackageProvenancesTable.scannerRunId,
            ScannerRunsTable.id
        )
        .join(ScannerJobsTable, JoinType.INNER, ScannerRunsTable.scannerJobId, ScannerJobsTable.id)
        .join(
            ScannerRunsScanResultsTable,
            JoinType.INNER,
            ScanResultsTable.id,
            ScannerRunsScanResultsTable.scanResultId
        ) { ScannerRunsScanResultsTable.scannerRunId eq ScannerRunsTable.id }
        .join(IdentifiersTable, JoinType.INNER, PackageProvenancesTable.identifierId, IdentifiersTable.id)
