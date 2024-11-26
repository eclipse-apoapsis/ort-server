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

package org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration

import org.eclipse.apoapsis.ortserver.model.runs.repository.SpdxLicenseChoice

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent a SPDX license choice, used within a [RepositoryConfiguration][RepositoryConfigurationsTable].
 */
object SpdxLicenseChoicesTable : LongIdTable("spdx_license_choices") {
    val given = text("given").nullable()
    val choice = text("choice")
}

class SpdxLicenseChoiceDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SpdxLicenseChoiceDao>(SpdxLicenseChoicesTable) {
        fun findBySpdxLicenseChoice(spdxLicenseChoice: SpdxLicenseChoice): SpdxLicenseChoiceDao? =
            find {
                SpdxLicenseChoicesTable.given eq spdxLicenseChoice.given and
                        (SpdxLicenseChoicesTable.choice eq spdxLicenseChoice.choice)
            }.firstOrNull()

        fun getOrPut(spdxLicenseChoice: SpdxLicenseChoice): SpdxLicenseChoiceDao =
            findBySpdxLicenseChoice(spdxLicenseChoice) ?: new {
                given = spdxLicenseChoice.given
                choice = spdxLicenseChoice.choice
            }
    }

    var given by SpdxLicenseChoicesTable.given
    var choice by SpdxLicenseChoicesTable.choice

    fun mapToModel() = SpdxLicenseChoice(given, choice)
}
