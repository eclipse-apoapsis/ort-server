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

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent an option for a scanner.
 */
object ScannerConfigurationOptionsTable : LongIdTable("scanner_configuration_options") {
    val scanner = text("scanner")
    val option = text("option")
    val value = text("value")
}

class ScannerConfigurationOptionDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScannerConfigurationOptionDao>(ScannerConfigurationOptionsTable) {
        fun find(scanner: String, option: String, value: String): ScannerConfigurationOptionDao? =
            find {
                ScannerConfigurationOptionsTable.scanner eq scanner and
                        (ScannerConfigurationOptionsTable.option eq option) and
                        (ScannerConfigurationOptionsTable.value eq value)
            }.firstOrNull()

        fun getOrPut(scanner: String, option: String, value: String): ScannerConfigurationOptionDao =
            find(scanner, option, value) ?: new {
                this.scanner = scanner
                this.option = option
                this.value = value
            }
    }

    var scanner by ScannerConfigurationOptionsTable.scanner
    var option by ScannerConfigurationOptionsTable.option
    var value by ScannerConfigurationOptionsTable.value
}
