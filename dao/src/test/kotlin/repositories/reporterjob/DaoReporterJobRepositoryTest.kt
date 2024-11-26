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

package org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob

import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Duration.Companion.minutes

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.repositories.WorkerJobRepositoryTest
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.model.util.asPresent

class DaoReporterJobRepositoryTest : WorkerJobRepositoryTest<ReporterJob>() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var reporterJobRepository: DaoReporterJobRepository
    private lateinit var jobConfigurations: JobConfigurations
    private lateinit var reporterJobConfiguration: ReporterJobConfiguration

    private var ortRunId = -1L

    override fun createJob() = reporterJobRepository.create(ortRunId, reporterJobConfiguration)

    override fun getJobRepository() = reporterJobRepository

    init {
        beforeEach {
            reporterJobRepository = dbExtension.fixtures.reporterJobRepository
            jobConfigurations = dbExtension.fixtures.jobConfigurations
            reporterJobConfiguration = jobConfigurations.reporter!!

            ortRunId = dbExtension.fixtures.ortRun.id
        }

        "create should create an entry in the database" {
            val createdReporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)

            val dbEntry = reporterJobRepository.get(createdReporterJob.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe ReporterJob(
                id = createdReporterJob.id,
                ortRunId = ortRunId,
                createdAt = createdReporterJob.createdAt,
                startedAt = null,
                finishedAt = null,
                configuration = reporterJobConfiguration,
                status = JobStatus.CREATED,
            )
        }

        "getForOrtRun should return the job for a run" {
            val reporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)

            reporterJobRepository.getForOrtRun(ortRunId) shouldBe reporterJob
        }

        "get should return null" {
            reporterJobRepository.get(1L).shouldBeNull()
        }

        "get should return the job" {
            val reporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)

            reporterJobRepository.get(reporterJob.id) shouldBe reporterJob
        }

        "update should update an entry in the database" {
            val reporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)

            val updateStartedAt = Clock.System.now().asPresent()
            val updatedFinishedAt = Clock.System.now().asPresent()
            val updateStatus = JobStatus.FINISHED.asPresent()

            val updateResult =
                reporterJobRepository.update(reporterJob.id, updateStartedAt, updatedFinishedAt, updateStatus)

            updateResult shouldBe reporterJob.copy(
                startedAt = updateStartedAt.value.toDatabasePrecision(),
                finishedAt = updatedFinishedAt.value.toDatabasePrecision(),
                status = updateStatus.value
            )
            reporterJobRepository.get(reporterJob.id) shouldBe reporterJob.copy(
                startedAt = updateStartedAt.value.toDatabasePrecision(),
                finishedAt = updatedFinishedAt.value.toDatabasePrecision(),
                status = updateStatus.value
            )
        }

        "delete should delete the database entry" {
            val reporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)

            reporterJobRepository.delete(reporterJob.id)

            reporterJobRepository.get(reporterJob.id) shouldBe null
        }

        "getReportByToken should return an existing report" {
            val reporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)
            val report = reporterJob.createRunWithReport(Clock.System.now().toDatabasePrecision().plus(10.minutes))

            val reportByToken = reporterJobRepository.getReportByToken(ortRunId, "report-token")

            reportByToken shouldBe report
        }

        "getReportByToken should return null if there is no ReporterRun" {
            reporterJobRepository.create(ortRunId, reporterJobConfiguration)

            reporterJobRepository.getReportByToken(ortRunId, "token") should beNull()
        }

        "getReportByToken should return null if the token cannot be resolved" {
            val reporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)
            reporterJob.createRunWithReport(Clock.System.now().plus(10.minutes))

            reporterJobRepository.getReportByToken(ortRunId, "invalid") should beNull()
        }

        "getReportByToken should return null if the token has expired" {
            val reporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)
            reporterJob.createRunWithReport(Clock.System.now().minus(10.minutes))

            reporterJobRepository.getReportByToken(ortRunId, "token") should beNull()
        }

        "getNonExpiredReports should return a report that has not expired" {
            val reporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)

            val report = reporterJob.createRunWithReport(Clock.System.now().toDatabasePrecision().plus(10.minutes))

            val nonExpiredReports = reporterJobRepository.getNonExpiredReports(ortRunId)

            nonExpiredReports shouldBe listOf(report)
        }

        "getNonExpiredReports should not return expired reports" {
            val reporterJob = reporterJobRepository.create(ortRunId, reporterJobConfiguration)

            reporterJob.createRunWithReport(Clock.System.now().toDatabasePrecision().minus(10.minutes))

            val nonExpiredReports = reporterJobRepository.getNonExpiredReports(ortRunId)

            nonExpiredReports shouldBe emptyList()
        }
    }

    /**
     * Create a run for this [ReporterJob] that contains a test report with the given token [expiryTime].
     */
    private fun ReporterJob.createRunWithReport(expiryTime: Instant): Report {
        val refTime = Clock.System.now().toDatabasePrecision()
        val downloadLink = "https://reports.example.org/ap1/v1/runs/42/downloads/report/report-token"
        val report = Report("file.pdf", downloadLink, expiryTime)
        val runRepository = dbExtension.fixtures.reporterRunRepository
        runRepository.create(id, refTime, refTime, listOf(report))

        return report
    }
}
