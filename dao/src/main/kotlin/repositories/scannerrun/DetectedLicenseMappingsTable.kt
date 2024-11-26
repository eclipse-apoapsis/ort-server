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
 * A table to store a detected license mapping.
 */
object DetectedLicenseMappingsTable : LongIdTable("detected_license_mappings") {
    val license = text("license")
    val spdxLicense = text("spdx_license")
}

class DetectedLicenseMappingDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<DetectedLicenseMappingDao>(DetectedLicenseMappingsTable) {
        fun findByDetectedLicenseMapping(licenseMapping: Pair<String, String>): DetectedLicenseMappingDao? =
            find {
                DetectedLicenseMappingsTable.license eq licenseMapping.first and
                        (DetectedLicenseMappingsTable.spdxLicense eq licenseMapping.second)
            }.firstOrNull()

        fun getOrPut(licenseMapping: Pair<String, String>): DetectedLicenseMappingDao =
            findByDetectedLicenseMapping(licenseMapping) ?: new {
                license = licenseMapping.first
                spdxLicense = licenseMapping.second
            }
    }

    var license by DetectedLicenseMappingsTable.license
    var spdxLicense by DetectedLicenseMappingsTable.spdxLicense
}
