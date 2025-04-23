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

package org.eclipse.apoapsis.ortserver.model.repositories

import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report

/**
 * A repository of [ReporterJob]s.
 */
interface ReporterJobRepository : WorkerJobRepository<ReporterJob> {
    /**
     * Create a reporter job.
     */
    fun create(ortRunId: Long, configuration: ReporterJobConfiguration): ReporterJob

    /**
     * Delete a reporter job by [id].
     */
    fun delete(id: Long)

    /**
     * Get a report for the given [ortRunId] by its [token]. The token allows access to the report without
     * authentication. Returns *null* if the token cannot be resolved or has expired.
     */
    fun getReportByToken(ortRunId: Long, token: String): Report?

    /**
     * Get all reports for the [ortRunId] filtered by the expiration date of the [Report.downloadLink].
     */
    fun getNonExpiredReports(ortRunId: Long): List<Report>
}
