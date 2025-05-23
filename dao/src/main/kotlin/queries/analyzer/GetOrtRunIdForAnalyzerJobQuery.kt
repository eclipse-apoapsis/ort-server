/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.queries.analyzer

import org.eclipse.apoapsis.ortserver.dao.Query
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable

/**
 * A query to get the ORT run ID for a given [analyzerJobId]. Returns `null` if the run is not found.
 */
class GetOrtRunIdForAnalyzerJobQuery(
    /** The ID of the analyzer job to retrieve the ORT run ID for. */
    val analyzerJobId: Long
) : Query<Long?> {
    override fun execute(): Long? =
        AnalyzerJobsTable
            .select(AnalyzerJobsTable.ortRunId)
            .where { AnalyzerJobsTable.id eq analyzerJobId }
            .map { it[AnalyzerJobsTable.ortRunId].value }
            .firstOrNull()
}
