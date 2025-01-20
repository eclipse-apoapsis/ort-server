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
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder

import java.util.concurrent.TimeUnit

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction

import org.slf4j.MDC

private val TIMER_STEP = 30.seconds

/**
 * A micrometer [MeterBinder] that provides metrics for ORT runs and jobs.
 */
class JobMetrics(private val application: Application) : MeterBinder {
    override fun bindTo(registry: MeterRegistry) {
        val analyzerTimer = Timer.builder("jobs.analyzer.duration")
            .description("The duration of analyzer jobs.")
            .register(registry)
        val advisorTimer = Timer.builder("jobs.advisor.duration")
            .description("The duration of advisor jobs.")
            .register(registry)
        val scannerTimer = Timer.builder("jobs.scanner.duration")
            .description("The duration of scanner jobs.")
            .register(registry)
        val evaluatorTimer = Timer.builder("jobs.evaluator.duration")
            .description("The duration of evaluator jobs.")
            .register(registry)
        val reporterTimer = Timer.builder("jobs.reporter.duration")
            .description("The duration of reporter jobs.")
            .register(registry)

        application.monitor.subscribe(DatabaseReady) {
            val component = MDC.get("component")

            OrtRunStatus.entries.forEach { status ->
                Gauge.builder("runs.status.${status.name.lowercase()}") {
                    MDC.put("component", component)
                    countOrtRunStatus(status)
                }.description("The number of ORT runs with status '${status.name}'.")
                    .register(registry)
            }

            JobStatus.entries.forEach { status ->
                Gauge.builder("jobs.advisor.status.${status.name.lowercase()}") {
                    MDC.put("component", component)
                    countAdvisorJobs(status)
                }.description("The number of advisor jobs with status '${status.name}'.")
                    .register(registry)

                Gauge.builder("jobs.analyzer.status.${status.name.lowercase()}") {
                    MDC.put("component", component)
                    countAnalyzerJobs(status)
                }.description("The number of analyzer jobs with status '${status.name}'.")
                    .register(registry)

                Gauge.builder("jobs.evaluator.status.${status.name.lowercase()}") {
                    MDC.put("component", component)
                    countEvaluatorJobs(status)
                }.description("The number of evaluator jobs with status '${status.name}'.")
                    .register(registry)

                Gauge.builder("jobs.reporter.status.${status.name.lowercase()}") {
                    MDC.put("component", component)
                    countReporterJobs(status)
                }.description("The number of reporter jobs with status '${status.name}'.")
                    .register(registry)

                Gauge.builder("jobs.scanner.status.${status.name.lowercase()}") {
                    MDC.put("component", component)
                    countScannerJobs(status)
                }.description("The number of scanner jobs with status '${status.name}'.")
                    .register(registry)
            }

            CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    val now = Clock.System.now()
                    getAnalyzerJobDurations(now - TIMER_STEP).forEach {
                        analyzerTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                    }

                    getAdvisorJobDurations(now - TIMER_STEP).forEach {
                        advisorTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                    }

                    getEvaluatorJobDurations(now - TIMER_STEP).forEach {
                        evaluatorTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                    }

                    getScannerJobDurations(now - TIMER_STEP).forEach {
                        scannerTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                    }

                    getReporterJobDurations(now - TIMER_STEP).forEach {
                        reporterTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                    }

                    delay(TIMER_STEP.inWholeMilliseconds)
                }
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

    private fun getAnalyzerJobDurations(timestamp: Instant): List<Duration> =
        transaction {
            AnalyzerJobsTable.select(AnalyzerJobsTable.startedAt, AnalyzerJobsTable.finishedAt).where {
                    AnalyzerJobsTable.status eq JobStatus.FINISHED or
                            (AnalyzerJobsTable.status eq JobStatus.FINISHED_WITH_ISSUES)
                }.andWhere {
                    AnalyzerJobsTable.startedAt.isNotNull()
                }.andWhere {
                    AnalyzerJobsTable.finishedAt.isNotNull()
                }.andWhere {
                    AnalyzerJobsTable.finishedAt greaterEq timestamp
            }.mapNotNull {
                val startedAt = it[AnalyzerJobsTable.startedAt] ?: return@mapNotNull null
                val finishedAt = it[AnalyzerJobsTable.finishedAt] ?: return@mapNotNull null
                finishedAt - startedAt
            }
        }

    private fun getAdvisorJobDurations(timestamp: Instant): List<Duration> =
        transaction {
            AdvisorJobsTable.select(AdvisorJobsTable.startedAt, AdvisorJobsTable.finishedAt).where {
                    AdvisorJobsTable.status eq JobStatus.FINISHED or
                            (AdvisorJobsTable.status eq JobStatus.FINISHED_WITH_ISSUES)
                }.andWhere {
                    AdvisorJobsTable.startedAt.isNotNull()
                }.andWhere {
                    AdvisorJobsTable.finishedAt.isNotNull()
                }.andWhere {
                    AdvisorJobsTable.finishedAt greaterEq timestamp
            }.mapNotNull {
                val startedAt = it[AdvisorJobsTable.startedAt] ?: return@mapNotNull null
                val finishedAt = it[AdvisorJobsTable.finishedAt] ?: return@mapNotNull null
                finishedAt - startedAt
            }
        }

    private fun getEvaluatorJobDurations(timestamp: Instant): List<Duration> =
        transaction {
            EvaluatorJobsTable.select(EvaluatorJobsTable.startedAt, EvaluatorJobsTable.finishedAt).where {
                    EvaluatorJobsTable.status eq JobStatus.FINISHED or
                            (EvaluatorJobsTable.status eq JobStatus.FINISHED_WITH_ISSUES)
                }.andWhere {
                    EvaluatorJobsTable.startedAt.isNotNull()
                }.andWhere {
                    EvaluatorJobsTable.finishedAt.isNotNull()
                }.andWhere {
                    EvaluatorJobsTable.finishedAt greaterEq timestamp
            }.mapNotNull {
                val startedAt = it[EvaluatorJobsTable.startedAt] ?: return@mapNotNull null
                val finishedAt = it[EvaluatorJobsTable.finishedAt] ?: return@mapNotNull null
                finishedAt - startedAt
            }
        }

    private fun getScannerJobDurations(timestamp: Instant): List<Duration> =
        transaction {
            ScannerJobsTable.select(ScannerJobsTable.startedAt, ScannerJobsTable.finishedAt).where {
                    ScannerJobsTable.status eq JobStatus.FINISHED or
                            (ScannerJobsTable.status eq JobStatus.FINISHED_WITH_ISSUES)
                }.andWhere {
                    ScannerJobsTable.startedAt.isNotNull()
                }.andWhere {
                    ScannerJobsTable.finishedAt.isNotNull()
                }.andWhere {
                    ScannerJobsTable.finishedAt greaterEq timestamp
            }.mapNotNull {
                val startedAt = it[ScannerJobsTable.startedAt] ?: return@mapNotNull null
                val finishedAt = it[ScannerJobsTable.finishedAt] ?: return@mapNotNull null
                finishedAt - startedAt
            }
        }

    private fun getReporterJobDurations(timestamp: Instant): List<Duration> =
        transaction {
            ReporterJobsTable.select(ReporterJobsTable.startedAt, ReporterJobsTable.finishedAt).where {
                    ReporterJobsTable.status eq JobStatus.FINISHED or
                            (ReporterJobsTable.status eq JobStatus.FINISHED_WITH_ISSUES)
                }.andWhere {
                    ReporterJobsTable.startedAt.isNotNull()
                }.andWhere {
                    ReporterJobsTable.finishedAt.isNotNull()
                }.andWhere {
                    ReporterJobsTable.finishedAt greaterEq timestamp
            }.mapNotNull {
                val startedAt = it[ReporterJobsTable.startedAt] ?: return@mapNotNull null
                val finishedAt = it[ReporterJobsTable.finishedAt] ?: return@mapNotNull null
                finishedAt - startedAt
            }
        }
}
