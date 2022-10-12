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
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentsTable
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision

/**
 * A table to represent an analyzer run.
 */
object AnalyzerRunsTable : LongIdTable("analyzer_runs") {
    val startTime = timestamp("start_time")
    val endTime = timestamp("end_time")
    val environment = reference("environment_id", EnvironmentsTable.id, ReferenceOption.CASCADE)
    val analyzerConfiguration =
        reference("analyzer_configuration_id", AnalyzerConfigurationsTable.id, ReferenceOption.CASCADE)
}

class AnalyzerRunDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AnalyzerRunDao>(AnalyzerRunsTable)

    var startTime by AnalyzerRunsTable.startTime.transform({ it.toDatabasePrecision() }, { it })
    var endTime by AnalyzerRunsTable.endTime.transform({ it.toDatabasePrecision() }, { it })
    var environment by EnvironmentDao referencedOn AnalyzerRunsTable.environment
    var analyzerConfiguration by AnalyzerConfigurationDao referencedOn AnalyzerRunsTable.analyzerConfiguration
}
