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

/**
 * A table to store scanner specific options of a scanner configuration.
 */
object ScannerConfigurationsScannerOptionsTable : LongIdTable("scanner_configurations_scanner_options") {
    val scannerConfigurationId = reference("scanner_configuration_id", ScannerConfigurationsTable)
    val scanner = text("scanner")
}

class ScannerConfigurationScannerOptionDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScannerConfigurationScannerOptionDao>(ScannerConfigurationsScannerOptionsTable)

    var scannerConfiguration by ScannerConfigurationDao referencedOn
            ScannerConfigurationsScannerOptionsTable.scannerConfigurationId

    var scanner by ScannerConfigurationsScannerOptionsTable.scanner
    val options by ScannerConfigurationOptionDao referrersOn
            ScannerConfigurationsOptionsTable.scannerOptionId
}
