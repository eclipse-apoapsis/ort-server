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

import org.eclipse.apoapsis.ortserver.model.PluginConfig
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerConfiguration

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to represent a scanner configuration.
 */
object ScannerConfigurationsTable : LongIdTable("scanner_configurations") {
    val scannerRunId = reference("scanner_run_id", ScannerRunsTable)

    val skipConcluded = bool("skip_concluded")
    val skipExcluded = bool("skip_excluded")
    val ignorePatterns = text("ignore_patterns").nullable()
}

class ScannerConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScannerConfigurationDao>(ScannerConfigurationsTable)

    var scannerRun by ScannerRunDao referencedOn ScannerConfigurationsTable.scannerRunId

    var skipConcluded by ScannerConfigurationsTable.skipConcluded
    var skipExcluded by ScannerConfigurationsTable.skipExcluded
    var ignorePatterns: List<String>? by ScannerConfigurationsTable.ignorePatterns
        .transform({ it?.joinToString(",") }, { it?.split(",") })
    var detectedLicenseMappings by DetectedLicenseMappingDao via
            ScannerConfigurationsDetectedLicenseMappingsTable
    var options by ScannerConfigurationOptionDao via ScannerConfigurationsOptionsTable
    var secrets by ScannerConfigurationSecretDao via ScannerConfigurationsSecretsTable

    fun mapToModel(): ScannerConfiguration {
        val optionsByScanner = options.groupBy { it.scanner }
            .mapValues { (_, value) -> value.associate { it.option to it.value } }
        val secretsByScanner = secrets.groupBy { it.scanner }
            .mapValues { (_, value) -> value.associate { it.secret to it.value } }

        val config = buildMap {
            (optionsByScanner.keys + secretsByScanner.keys).forEach { scanner ->
                val pluginConfig = PluginConfig(
                    options = optionsByScanner[scanner].orEmpty(),
                    secrets = secretsByScanner[scanner].orEmpty()
                )

                put(scanner, pluginConfig)
            }
        }

        return ScannerConfiguration(
            skipConcluded = skipConcluded,
            skipExcluded = skipExcluded,
            detectedLicenseMappings = detectedLicenseMappings.associate { it.license to it.spdxLicense },
            config = config,
            ignorePatterns = ignorePatterns.orEmpty()
        )
    }
}
