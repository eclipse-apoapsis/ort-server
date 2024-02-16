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

package org.ossreviewtoolkit.server.dao.repositories

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.ReporterJob
import org.ossreviewtoolkit.server.model.runs.reporter.Report
import org.ossreviewtoolkit.server.model.runs.reporter.ReporterRun

class DaoReporterRunRepositoryTest : StringSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var reporterRunRepository: DaoReporterRunRepository
    lateinit var reporterJob: ReporterJob

    val reports = listOf(Report("file1.pdf"), Report("file2.pdf"))
    val time = Clock.System.now().toDatabasePrecision()

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
