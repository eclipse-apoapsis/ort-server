/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.utils

import io.ktor.server.application.Application

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.server.core.plugins.DatabaseReady
import org.ossreviewtoolkit.server.dao.tables.AdvisorJobDao
import org.ossreviewtoolkit.server.dao.tables.AdvisorJobsTable
import org.ossreviewtoolkit.server.dao.tables.AnalyzerJobDao
import org.ossreviewtoolkit.server.dao.tables.AnalyzerJobsTable
import org.ossreviewtoolkit.server.dao.tables.EvaluatorJobDao
import org.ossreviewtoolkit.server.dao.tables.EvaluatorJobsTable
import org.ossreviewtoolkit.server.dao.tables.OrtRunDao
import org.ossreviewtoolkit.server.dao.tables.OrtRunsTable
import org.ossreviewtoolkit.server.dao.tables.ReporterJobDao
import org.ossreviewtoolkit.server.dao.tables.ReporterJobsTable
import org.ossreviewtoolkit.server.dao.tables.ScannerJobDao
import org.ossreviewtoolkit.server.dao.tables.ScannerJobsTable
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.OrtRunStatus

/**
 * A micrometer [MeterBinder] that provides metrics for ORT runs and jobs.
 */
@OptIn(ExperimentalStdlibApi::class)
class JobMetrics(private val application: Application) : MeterBinder {
    override fun bindTo(registry: MeterRegistry) {
        application.environment.monitor.subscribe(DatabaseReady) {
            OrtRunStatus.entries.forEach { status ->
                Gauge.builder("runs.status.${status.name.lowercase()}") { countOrtRunStatus(status) }
                    .description("The number of ORT runs with status '${status.name}'.")
                    .register(registry)
            }

            JobStatus.entries.forEach { status ->
                Gauge.builder("jobs.advisor.status.${status.name.lowercase()}") { countAdvisorJobs(status) }
                    .description("The number of advisor jobs with status '${status.name}'.")
                    .register(registry)

                Gauge.builder("jobs.analyzer.status.${status.name.lowercase()}") { countAnalyzerJobs(status) }
                    .description("The number of analyzer jobs with status '${status.name}'.")
                    .register(registry)

                Gauge.builder("jobs.evaluator.status.${status.name.lowercase()}") { countEvaluatorJobs(status) }
                    .description("The number of evaluator jobs with status '${status.name}'.")
                    .register(registry)

                Gauge.builder("jobs.reporter.status.${status.name.lowercase()}") { countReporterJobs(status) }
                    .description("The number of reporter jobs with status '${status.name}'.")
                    .register(registry)

                Gauge.builder("jobs.scanner.status.${status.name.lowercase()}") { countScannerJobs(status) }
                    .description("The number of scanner jobs with status '${status.name}'.")
                    .register(registry)
            }
        }
    }

    private fun countOrtRunStatus(status: OrtRunStatus) =
        transaction { OrtRunDao.count(OrtRunsTable.status eq status).toDouble() }

    private fun countAdvisorJobs(status: JobStatus) =
        transaction { AdvisorJobDao.count(AdvisorJobsTable.status eq status).toDouble() }

    private fun countAnalyzerJobs(status: JobStatus) =
        transaction { AnalyzerJobDao.count(AnalyzerJobsTable.status eq status).toDouble() }

    private fun countEvaluatorJobs(status: JobStatus) =
        transaction { EvaluatorJobDao.count(EvaluatorJobsTable.status eq status).toDouble() }

    private fun countReporterJobs(status: JobStatus) =
        transaction { ReporterJobDao.count(ReporterJobsTable.status eq status).toDouble() }

    private fun countScannerJobs(status: JobStatus) =
        transaction { ScannerJobDao.count(ScannerJobsTable.status eq status).toDouble() }
}
