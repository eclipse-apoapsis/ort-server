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

package org.eclipse.apoapsis.ortserver.core.utils

import io.ktor.server.application.Application

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder

import org.eclipse.apoapsis.ortserver.core.plugins.DatabaseReady
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.AdvisorJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.AdvisorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.EvaluatorJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.EvaluatorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob.ReporterJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob.ReporterJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.ScannerJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.ScannerJobsTable
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

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
