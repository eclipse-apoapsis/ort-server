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
import org.jetbrains.exposed.sql.ReferenceOption

import org.ossreviewtoolkit.server.model.runs.CuratedPackage

/**
 * An intermediate table to store references from [PackagesTable] and [AnalyzerRunsTable].
 */
object CuratedPackagesTable : LongIdTable("curated_packages") {
    val packageId = reference("package_id", PackagesTable.id, ReferenceOption.CASCADE)
    val analyzerRunId = reference("analyzer_run_id", AnalyzerRunsTable.id, ReferenceOption.CASCADE)
}

class CuratedPackageDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<CuratedPackageDao>(CuratedPackagesTable)

    var packageId by PackageDao referencedOn CuratedPackagesTable.packageId
    var analyzerRun by AnalyzerRunDao referencedOn CuratedPackagesTable.analyzerRunId
    val curations by PackageCurationResultDao referrersOn PackagesCurationResultsTable.curatedPackageId

    fun mapToModel() = CuratedPackage(
        id.value,
        packageId.mapToModel(),
        curations.map(PackageCurationResultDao::mapToModel)
    )
}
