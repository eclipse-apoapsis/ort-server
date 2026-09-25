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
import io.ktor.server.application.ApplicationStarted
import io.ktor.utils.io.CancellationException

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.slf4j.MDCContext

import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.AdvisorJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.AdvisorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.AnalyzerJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.EvaluatorJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.EvaluatorJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.notifierjob.NotifierJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.notifierjob.NotifierJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunDao
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.OrtRunsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob.ReporterJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob.ReporterJobsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.ScannerJobDao
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.ScannerJobsTable
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.shared.coroutines.Virtual

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select

import org.slf4j.LoggerFactory

private val TIMER_STEP = 30.seconds

private val logger = LoggerFactory.getLogger(JobMetrics::class.java)

/**
 * A micrometer [MeterBinder] that provides metrics for ORT runs and jobs.
 */
@Suppress("TooManyFunctions")
class JobMetrics(private val application: Application, private val db: Database) : MeterBinder {
    private val runStatusCounts = OrtRunStatus.entries.associateWith { AtomicInteger(0) }
    private val advisorStatusCounts = JobStatus.entries.associateWith { AtomicInteger(0) }
    private val analyzerStatusCounts = JobStatus.entries.associateWith { AtomicInteger(0) }
    private val evaluatorStatusCounts = JobStatus.entries.associateWith { AtomicInteger(0) }
    private val notifierStatusCounts = JobStatus.entries.associateWith { AtomicInteger(0) }
    private val reporterStatusCounts = JobStatus.entries.associateWith { AtomicInteger(0) }
    private val scannerStatusCounts = JobStatus.entries.associateWith { AtomicInteger(0) }

    @Suppress("TooGenericExceptionCaught")
    override fun bindTo(registry: MeterRegistry) {
        val ortRunTimer = Timer.builder("runs.duration")
            .description("The duration of ORT runs.")
            .register(registry)

        val analyzerTimer = Timer.builder("jobs.analyzer.duration")
            .description("The duration of analyzer jobs.")
            .register(registry)
        val analyzerQueueTimer = Timer.builder("jobs.analyzer.queue.duration")
            .description("The duration of analyzer jobs in the queue.")
            .register(registry)
        val advisorTimer = Timer.builder("jobs.advisor.duration")
            .description("The duration of advisor jobs.")
            .register(registry)
        val advisorQueueTimer = Timer.builder("jobs.advisor.queue.duration")
            .description("The duration of advisor jobs in the queue.")
            .register(registry)
        val scannerTimer = Timer.builder("jobs.scanner.duration")
            .description("The duration of scanner jobs.")
            .register(registry)
        val scannerQueueTimer = Timer.builder("jobs.scanner.queue.duration")
            .description("The duration of scanner jobs in the queue.")
            .register(registry)
        val evaluatorTimer = Timer.builder("jobs.evaluator.duration")
            .description("The duration of evaluator jobs.")
            .register(registry)
        val evaluatorQueueTimer = Timer.builder("jobs.evaluator.queue.duration")
            .description("The duration of evaluator jobs in the queue.")
            .register(registry)
        val reporterTimer = Timer.builder("jobs.reporter.duration")
            .description("The duration of reporter jobs.")
            .register(registry)
        val reporterQueueTimer = Timer.builder("jobs.reporter.queue.duration")
            .description("The duration of reporter jobs in the queue.")
            .register(registry)
        val notifierTimer = Timer.builder("jobs.notifier.duration")
            .description("The duration of notifier jobs.")
            .register(registry)
        val notifierQueueTimer = Timer.builder("jobs.notifier.queue.duration")
            .description("The duration of notifier jobs in the queue.")
            .register(registry)

        application.monitor.subscribe(ApplicationStarted) {
            OrtRunStatus.entries.forEach { status ->
                Gauge.builder(
                    "runs.status.${status.name.lowercase()}",
                    runStatusCounts[status]
                ) { it.toDouble() }
                    .description("The number of ORT runs with status '${status.name}'.")
                    .register(registry)
            }

            JobStatus.entries.forEach { status ->
                Gauge.builder(
                    "jobs.advisor.status.${status.name.lowercase()}",
                    advisorStatusCounts[status]
                ) { it.toDouble() }
                    .description("The number of advisor jobs with status '${status.name}'.")
                    .register(registry)

                Gauge.builder(
                    "jobs.analyzer.status.${status.name.lowercase()}",
                    analyzerStatusCounts[status]
                ) { it.toDouble() }
                    .description("The number of analyzer jobs with status '${status.name}'.")
                    .register(registry)

                Gauge.builder(
                    "jobs.evaluator.status.${status.name.lowercase()}",
                    evaluatorStatusCounts[status]
                ) { it.toDouble() }
                    .description("The number of evaluator jobs with status '${status.name}'.")
                    .register(registry)

                Gauge.builder(
                    "jobs.notifier.status.${status.name.lowercase()}",
                    notifierStatusCounts[status]
                ) { it.toDouble() }
                    .description("The number of notifier jobs with status '${status.name}'.")
                    .register(registry)

                Gauge.builder(
                    "jobs.reporter.status.${status.name.lowercase()}",
                    reporterStatusCounts[status]
                ) { it.toDouble() }
                    .description("The number of reporter jobs with status '${status.name}'.")
                    .register(registry)

                Gauge.builder(
                    "jobs.scanner.status.${status.name.lowercase()}",
                    scannerStatusCounts[status]
                ) { it.toDouble() }
                    .description("The number of scanner jobs with status '${status.name}'.")
                    .register(registry)
            }

            CoroutineScope(Dispatchers.Virtual + MDCContext()).launch {
                while (isActive) {
                    val now = Clock.System.now()

                    try {
                        OrtRunStatus.entries.forEach { status ->
                            runStatusCounts[status]?.set(countOrtRunStatus(status).toInt())
                        }

                        JobStatus.entries.forEach { status ->
                            advisorStatusCounts[status]?.set(countAdvisorJobs(status).toInt())
                            analyzerStatusCounts[status]?.set(countAnalyzerJobs(status).toInt())
                            evaluatorStatusCounts[status]?.set(countEvaluatorJobs(status).toInt())
                            notifierStatusCounts[status]?.set(countNotifierJobs(status).toInt())
                            reporterStatusCounts[status]?.set(countReporterJobs(status).toInt())
                            scannerStatusCounts[status]?.set(countScannerJobs(status).toInt())
                        }

                        getOrtRunDurations(now - TIMER_STEP).forEach {
                            ortRunTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                        }

                        getAnalyzerJobDurations(now - TIMER_STEP).forEach {
                            analyzerTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                        }

                        getAnalyzerJobQueueDurations(now - TIMER_STEP).forEach {
                            analyzerQueueTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                        }

                        getAdvisorJobDurations(now - TIMER_STEP).forEach {
                            advisorTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                        }

                        getAdvisorJobQueueDurations(now - TIMER_STEP).forEach {
                            advisorQueueTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                        }

                        getEvaluatorJobDurations(now - TIMER_STEP).forEach {
                            evaluatorTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                        }

                        getEvaluatorJobQueueDurations(now - TIMER_STEP).forEach {
                            evaluatorQueueTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                        }

                        getScannerJobDurations(now - TIMER_STEP).forEach {
                            scannerTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                        }

                        getScannerJobQueueDurations(now - TIMER_STEP).forEach {
                            scannerQueueTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                        }

                        getReporterJobDurations(now - TIMER_STEP).forEach {
                            reporterTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                        }

                        getReporterJobQueueDurations(now - TIMER_STEP).forEach {
                            reporterQueueTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                        }

                        getNotifierJobDurations(now - TIMER_STEP).forEach {
                            notifierTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                        }

                        getNotifierJobQueueDurations(now - TIMER_STEP).forEach {
                            notifierQueueTimer.record(it.inWholeSeconds, TimeUnit.SECONDS)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.warn("Failed to update job metrics.", e)
                    }

                    delay(TIMER_STEP)
                }
            }
        }
    }

    private suspend fun countOrtRunStatus(status: OrtRunStatus) =
        db.transaction { OrtRunDao.count(OrtRunsTable.status eq status).toDouble() }

    private suspend fun countAdvisorJobs(status: JobStatus) =
        db.transaction { AdvisorJobDao.count(AdvisorJobsTable.status eq status).toDouble() }

    private suspend fun countAnalyzerJobs(status: JobStatus) =
        db.transaction { AnalyzerJobDao.count(AnalyzerJobsTable.status eq status).toDouble() }

    private suspend fun countEvaluatorJobs(status: JobStatus) =
        db.transaction { EvaluatorJobDao.count(EvaluatorJobsTable.status eq status).toDouble() }

    private suspend fun countNotifierJobs(status: JobStatus) =
        db.transaction { NotifierJobDao.count(NotifierJobsTable.status eq status).toDouble() }

    private suspend fun countReporterJobs(status: JobStatus) =
        db.transaction { ReporterJobDao.count(ReporterJobsTable.status eq status).toDouble() }

    private suspend fun countScannerJobs(status: JobStatus) =
        db.transaction { ScannerJobDao.count(ScannerJobsTable.status eq status).toDouble() }

    private suspend fun getAnalyzerJobDurations(timestamp: Instant): List<Duration> =
        db.transaction {
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

    private suspend fun getAnalyzerJobQueueDurations(timestamp: Instant): List<Duration> =
        db.transaction {
            AnalyzerJobsTable.select(AnalyzerJobsTable.createdAt, AnalyzerJobsTable.startedAt).where {
                AnalyzerJobsTable.status eq JobStatus.RUNNING
            }.andWhere {
                AnalyzerJobsTable.startedAt.isNotNull()
            }.andWhere {
                AnalyzerJobsTable.finishedAt.isNull()
            }.andWhere {
                AnalyzerJobsTable.startedAt greaterEq timestamp
            }.mapNotNull {
                val createdAt = it[AnalyzerJobsTable.createdAt]
                val startedAt = it[AnalyzerJobsTable.startedAt] ?: return@mapNotNull null
                startedAt - createdAt
            }
        }

    private suspend fun getAdvisorJobDurations(timestamp: Instant): List<Duration> =
        db.transaction {
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

    private suspend fun getAdvisorJobQueueDurations(timestamp: Instant): List<Duration> =
        db.transaction {
            AdvisorJobsTable.select(AdvisorJobsTable.createdAt, AdvisorJobsTable.startedAt).where {
                AdvisorJobsTable.status eq JobStatus.RUNNING
            }.andWhere {
                AdvisorJobsTable.startedAt.isNotNull()
            }.andWhere {
                AdvisorJobsTable.finishedAt.isNull()
            }.andWhere {
                AdvisorJobsTable.startedAt greaterEq timestamp
            }.mapNotNull {
                val createdAt = it[AdvisorJobsTable.createdAt]
                val startedAt = it[AdvisorJobsTable.startedAt] ?: return@mapNotNull null
                startedAt - createdAt
            }
        }

    private suspend fun getEvaluatorJobDurations(timestamp: Instant): List<Duration> =
        db.transaction {
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

    private suspend fun getEvaluatorJobQueueDurations(timestamp: Instant): List<Duration> =
        db.transaction {
            EvaluatorJobsTable.select(EvaluatorJobsTable.createdAt, EvaluatorJobsTable.startedAt).where {
                EvaluatorJobsTable.status eq JobStatus.RUNNING
            }.andWhere {
                EvaluatorJobsTable.startedAt.isNotNull()
            }.andWhere {
                EvaluatorJobsTable.finishedAt.isNull()
            }.andWhere {
                EvaluatorJobsTable.startedAt greaterEq timestamp
            }.mapNotNull {
                val createdAt = it[EvaluatorJobsTable.createdAt]
                val startedAt = it[EvaluatorJobsTable.startedAt] ?: return@mapNotNull null
                startedAt - createdAt
            }
        }

    private suspend fun getScannerJobDurations(timestamp: Instant): List<Duration> =
        db.transaction {
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

    private suspend fun getScannerJobQueueDurations(timestamp: Instant): List<Duration> =
        db.transaction {
            ScannerJobsTable.select(ScannerJobsTable.createdAt, ScannerJobsTable.startedAt).where {
                ScannerJobsTable.status eq JobStatus.RUNNING
            }.andWhere {
                ScannerJobsTable.startedAt.isNotNull()
            }.andWhere {
                ScannerJobsTable.finishedAt.isNull()
            }.andWhere {
                ScannerJobsTable.startedAt greaterEq timestamp
            }.mapNotNull {
                val createdAt = it[ScannerJobsTable.createdAt]
                val startedAt = it[ScannerJobsTable.startedAt] ?: return@mapNotNull null
                startedAt - createdAt
            }
        }

    private suspend fun getReporterJobDurations(timestamp: Instant): List<Duration> =
        db.transaction {
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

    private suspend fun getReporterJobQueueDurations(timestamp: Instant): List<Duration> =
        db.transaction {
            ReporterJobsTable.select(ReporterJobsTable.createdAt, ReporterJobsTable.startedAt).where {
                ReporterJobsTable.status eq JobStatus.RUNNING
            }.andWhere {
                ReporterJobsTable.startedAt.isNotNull()
            }.andWhere {
                ReporterJobsTable.finishedAt.isNull()
            }.andWhere {
                ReporterJobsTable.startedAt greaterEq timestamp
            }.mapNotNull {
                val createdAt = it[ReporterJobsTable.createdAt]
                val startedAt = it[ReporterJobsTable.startedAt] ?: return@mapNotNull null
                startedAt - createdAt
            }
        }

    private suspend fun getNotifierJobDurations(timestamp: Instant): List<Duration> =
        db.transaction {
            NotifierJobsTable.select(NotifierJobsTable.startedAt, NotifierJobsTable.finishedAt).where {
                NotifierJobsTable.status eq JobStatus.FINISHED or
                        (NotifierJobsTable.status eq JobStatus.FINISHED_WITH_ISSUES)
            }.andWhere {
                NotifierJobsTable.startedAt.isNotNull()
            }.andWhere {
                NotifierJobsTable.finishedAt.isNotNull()
            }.andWhere {
                NotifierJobsTable.finishedAt greaterEq timestamp
            }.mapNotNull {
                val startedAt = it[NotifierJobsTable.startedAt] ?: return@mapNotNull null
                val finishedAt = it[NotifierJobsTable.finishedAt] ?: return@mapNotNull null
                finishedAt - startedAt
            }
        }

    private suspend fun getNotifierJobQueueDurations(timestamp: Instant): List<Duration> =
        db.transaction {
            NotifierJobsTable.select(NotifierJobsTable.createdAt, NotifierJobsTable.startedAt).where {
                NotifierJobsTable.status eq JobStatus.RUNNING
            }.andWhere {
                NotifierJobsTable.startedAt.isNotNull()
            }.andWhere {
                NotifierJobsTable.finishedAt.isNull()
            }.andWhere {
                NotifierJobsTable.startedAt greaterEq timestamp
            }.mapNotNull {
                val createdAt = it[NotifierJobsTable.createdAt]
                val startedAt = it[NotifierJobsTable.startedAt] ?: return@mapNotNull null
                startedAt - createdAt
            }
        }

    private suspend fun getOrtRunDurations(instant: Instant): List<Duration> =
        db.transaction {
            OrtRunsTable.select(OrtRunsTable.createdAt, OrtRunsTable.finishedAt).where {
                OrtRunsTable.status eq OrtRunStatus.FINISHED or
                        (OrtRunsTable.status eq OrtRunStatus.FINISHED_WITH_ISSUES)
            }.andWhere {
                OrtRunsTable.finishedAt.isNotNull()
            }.andWhere {
                OrtRunsTable.finishedAt greaterEq instant
            }.mapNotNull {
                val createdAt = it[OrtRunsTable.createdAt]
                val finishedAt = it[OrtRunsTable.finishedAt] ?: return@mapNotNull null
                finishedAt - createdAt
            }
        }
}
