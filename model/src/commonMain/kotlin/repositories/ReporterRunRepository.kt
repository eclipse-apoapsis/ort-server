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

import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.model.runs.reporter.ReporterRun

/**
 * A repository of [reporter runs][ReporterRun].
 */
interface ReporterRunRepository {
    /**
     * Create a reporter run.
     */
    fun create(
        reporterJobId: Long,
        startTime: Instant,
        endTime: Instant,
        reports: List<Report>
    ): ReporterRun

    /**
     * Get a reporter run by [id]. Returns null if the reporter run is not found.
     */
    fun get(id: Long): ReporterRun?

    /**
     * Get a reporter run by [reporterJobId]. Returns null if the reporter run is not found.
     */
    fun getByJobId(reporterJobId: Long): ReporterRun?
}
