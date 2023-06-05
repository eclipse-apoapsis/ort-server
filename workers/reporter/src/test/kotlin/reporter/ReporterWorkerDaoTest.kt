/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.reporter

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorConfiguration
import org.ossreviewtoolkit.utils.common.gibibytes

class ReporterWorkerDaoTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var dao: ReporterWorkerDao
    lateinit var fixtures: Fixtures

    beforeEach {
        dao = ReporterWorkerDao(
            advisorJobRepository = dbExtension.fixtures.advisorJobRepository,
            advisorRunRepository = dbExtension.fixtures.advisorRunRepository,
            analyzerJobRepository = dbExtension.fixtures.analyzerJobRepository,
            analyzerRunRepository = dbExtension.fixtures.analyzerRunRepository,
            evaluatorJobRepository = dbExtension.fixtures.evaluatorJobRepository,
            evaluatorRunRepository = dbExtension.fixtures.evaluatorRunRepository,
            ortRunRepository = dbExtension.fixtures.ortRunRepository,
            reporterJobRepository = dbExtension.fixtures.reporterJobRepository,
            repositoryRepository = dbExtension.fixtures.repositoryRepository
        )
        fixtures = dbExtension.fixtures
    }

    "getReporterJob" should {
        "return job if job does exist" {
            val job = dao.getReporterJob(fixtures.reporterJob.id)

            job shouldBe fixtures.reporterJob
        }

        "return null if job does not exist" {
            dao.getReporterJob(-1L) shouldBe null
        }
    }

    "getOrtRun" should {
        "return an ort run" {
            val ortRun = dao.getOrtRun(fixtures.ortRun.id)

            ortRun shouldBe fixtures.ortRun
        }

        "return null if run does not exist" {
            dao.getOrtRun(-1L) shouldBe null
        }
    }

    "getRepository" should {
        "return a repository" {
            val repository = dao.getRepository(fixtures.repository.id)

            repository shouldBe fixtures.repository
        }

        "return null if repository does not exist" {
            dao.getRepository(-1L) shouldBe null
        }
    }

    "getAnalyzerRunForReporterJob" should {
        "return an analyzer run" {
            val createdAnalyzerRun = dbExtension.fixtures.analyzerRunRepository.create(
                analyzerJobId = fixtures.analyzerJob.id,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                environment = Environment(
                    ortVersion = "1.0.0",
                    javaVersion = "17",
                    os = "Linux",
                    processors = 8,
                    maxMemory = 16.gibibytes,
                    variables = emptyMap(),
                    toolVersions = emptyMap()
                ),
                config = AnalyzerConfiguration(),
                projects = emptySet(),
                packages = emptySet(),
                issues = emptyMap(),
                dependencyGraphs = emptyMap()
            )

            val requestedAnalyzerRun = dao.getAnalyzerRunForReporterJob(fixtures.reporterJob)

            requestedAnalyzerRun shouldBe createdAnalyzerRun
        }

        "return null if run does not exist" {
            dao.getAnalyzerRunForReporterJob(fixtures.reporterJob.copy(ortRunId = -1L)) shouldBe null
        }
    }

    "getAdvisorRunForReporterJob" should {
        "return an advisor run" {
            val createdAdvisorRun = dbExtension.fixtures.advisorRunRepository.create(
                advisorJobId = fixtures.advisorJob.id,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                environment = Environment(
                    ortVersion = "1.0.0",
                    javaVersion = "17",
                    os = "Linux",
                    processors = 8,
                    maxMemory = 16.gibibytes,
                    variables = emptyMap(),
                    toolVersions = emptyMap()
                ),
                config = AdvisorConfiguration(null, null, null, null, emptyMap()),
                advisorRecords = emptyMap()
            )

            val requestedAdvisorRun = dao.getAdvisorRunForReporterJob(fixtures.reporterJob)

            requestedAdvisorRun shouldBe createdAdvisorRun
        }

        "return null if run does not exist" {
            dao.getAdvisorRunForReporterJob(fixtures.reporterJob.copy(ortRunId = -1L)) shouldBe null
        }
    }

    "getEvaluatorRunForReporterJob" should {
        "return an evaluator run" {
            val createdEvaluatorRun = dbExtension.fixtures.evaluatorRunRepository.create(
                evaluatorJobId = fixtures.evaluatorJob.id,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                violations = emptyList()
            )

            val requestedEvaluatorRun = dao.getEvaluatorRunForReporterJob(fixtures.reporterJob)

            requestedEvaluatorRun shouldBe createdEvaluatorRun
        }

        "return null if run does not exist" {
            dao.getEvaluatorRunForReporterJob(fixtures.reporterJob.copy(ortRunId = -1L)) shouldBe null
        }
    }
})
