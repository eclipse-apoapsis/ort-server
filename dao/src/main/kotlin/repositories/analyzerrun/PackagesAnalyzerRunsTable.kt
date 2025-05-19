/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import org.eclipse.apoapsis.ortserver.model.runs.Package

import org.jetbrains.exposed.sql.Table

/**
 * An intermediate table to store references from [PackagesTable] and [AnalyzerRunsTable].
 */
object PackagesAnalyzerRunsTable : Table("packages_analyzer_runs") {
    val packageId = reference("package_id", PackagesTable)
    val analyzerRunId = reference("analyzer_run_id", AnalyzerRunsTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(packageId, analyzerRunId, name = "${tableName}_pkey")

    /** Get the [Package]s for the given [analyzerRunId]. */
    fun getPackagesByAnalyzerRunId(analyzerRunId: Long): Set<Package> {
        val packageIds = select(packageId)
            .where { PackagesAnalyzerRunsTable.analyzerRunId eq analyzerRunId }
            .mapTo(mutableSetOf()) { it[packageId].value }

        return PackagesTable.getByIds(packageIds)
    }
}
