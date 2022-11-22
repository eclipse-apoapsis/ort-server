/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.tables.runs.analyzer

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

import org.ossreviewtoolkit.server.dao.tables.runs.shared.LicenseSpdxDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.LicenseSpdxTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.LicenseStringDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.LicenseStringsTable

/**
 * An intermediate table to store references from [ProcessedDeclaredLicensesTable], [LicenseStringsTable]
 * and [LicenseSpdxTable].
 */
object ProcessedDeclaredLicensesMappedLicensesTable : LongIdTable("processed_declared_licenses_mapped_licenses") {
    val processedDeclaredLicenseId = reference("processed_declared_license_id", ProcessedDeclaredLicensesTable)
    val licenseStringId = reference("license_string_id", LicenseStringsTable)
    val licenseSpdxId = reference("license_spdx_id", LicenseSpdxTable)
}

class ProcessedDeclaredLicensesMappedLicenseDao(id: EntityID<Long>) : LongEntity(id) {
    companion object :
        LongEntityClass<ProcessedDeclaredLicensesMappedLicenseDao>(ProcessedDeclaredLicensesMappedLicensesTable)

    var processedDeclaredLicenseDao by ProcessedDeclaredLicenseDao referencedOn
            ProcessedDeclaredLicensesMappedLicensesTable.processedDeclaredLicenseId
    var licenseString by LicenseStringDao referencedOn ProcessedDeclaredLicensesMappedLicensesTable.licenseStringId
    var licenseSpdx by LicenseSpdxDao referencedOn ProcessedDeclaredLicensesMappedLicensesTable.licenseSpdxId
}
