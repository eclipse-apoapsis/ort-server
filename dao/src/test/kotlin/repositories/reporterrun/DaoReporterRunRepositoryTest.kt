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

package org.eclipse.apoapsis.ortserver.dao.repositories.reporterrun

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlin.time.Duration.Companion.minutes

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.model.runs.reporter.ReporterRun

class DaoReporterRunRepositoryTest : StringSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var reporterRunRepository: DaoReporterRunRepository
    lateinit var reporterJob: ReporterJob

    val time = Clock.System.now().toDatabasePrecision()
    val reports = listOf(
        Report("file1.pdf", "token1", time.plus(10.minutes)),
        Report("file2.pdf", "token2", time.minus(10.minutes))
    )

    beforeEach {
        reporterRunRepository = dbExtension.fixtures.reporterRunRepository
        reporterJob = dbExtension.fixtures.reporterJob
    }

    "create should create an entry in the database" {
        val reporterRun = reporterRunRepository.create(reporterJob.id, time, time, reports)

        val dbEntry = reporterRunRepository.get(reporterRun.id)

        dbEntry.shouldNotBeNull()
        dbEntry shouldBe ReporterRun(
            id = reporterRun.id,
            reporterJobId = reporterJob.id,
            startTime = time,
            endTime = time,
            reports = reports
        )
    }

    "getByJobId should return the reporter run for a job" {
        val reporterRun = reporterRunRepository.create(reporterJob.id, time, time, reports)

        reporterRunRepository.getByJobId(reporterJob.id) shouldBe reporterRun
    }

    "get should return the reporter run" {
        val reporterRun = reporterRunRepository.create(reporterJob.id, time, time, reports)

        reporterRunRepository.get(reporterRun.id) shouldBe reporterRun
    }
})
