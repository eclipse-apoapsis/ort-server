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

package org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent mapped declared licenses that belong to a
 * [processed declared license][ProcessedDeclaredLicensesTable].
 */
object MappedDeclaredLicensesTable : LongIdTable("mapped_declared_licenses") {
    val declaredLicense = text("declared_license")
    val mappedLicense = text("mapped_license")
}

class MappedDeclaredLicenseDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<MappedDeclaredLicenseDao>(MappedDeclaredLicensesTable) {
        fun findByMapping(declaredLicense: String, mappedLicense: String): MappedDeclaredLicenseDao? =
            find {
                MappedDeclaredLicensesTable.declaredLicense eq declaredLicense and
                        (MappedDeclaredLicensesTable.mappedLicense eq mappedLicense)
            }.firstOrNull()

        fun getOrPut(declaredLicense: String, mappedLicense: String): MappedDeclaredLicenseDao =
            findByMapping(declaredLicense, mappedLicense) ?: new {
                this.declaredLicense = declaredLicense
                this.mappedLicense = mappedLicense
            }
    }

    var declaredLicense by MappedDeclaredLicensesTable.declaredLicense
    var mappedLicense by MappedDeclaredLicensesTable.mappedLicense

    val processedDeclaredLicenses by ProcessedDeclaredLicenseDao via
            ProcessedDeclaredLicensesMappedDeclaredLicensesTable
}
