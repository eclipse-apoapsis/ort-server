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

import org.ossreviewtoolkit.server.model.runs.PackageCurationResult

/**
 * An intermediate table to store references from [PackageCurationDataTable] and [CuratedPackagesTable].
 */
object PackagesCurationResultsTable : LongIdTable("packages_curation_results") {
    val baseCuration = reference("base_curation_id", PackageCurationDataTable)
    val appliedCuration = reference("applied_curation_id", PackageCurationDataTable)
    val curatedPackage = reference("curated_package_id", CuratedPackagesTable)
}

class PackageCurationResultDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PackageCurationResultDao>(PackagesCurationResultsTable)

    var baseCuration by PackageCurationDataDao referencedOn PackagesCurationResultsTable.baseCuration
    var appliedCuration by PackageCurationDataDao referencedOn PackagesCurationResultsTable.appliedCuration
    var curatedPackage by CuratedPackageDao referencedOn PackagesCurationResultsTable.curatedPackage

    fun mapToModel() = PackageCurationResult(id.value, baseCuration.mapToModel(), appliedCuration.mapToModel())
}
