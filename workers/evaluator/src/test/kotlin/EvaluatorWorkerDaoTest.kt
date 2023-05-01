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

package org.ossreviewtoolkit.server.workers.evaluator

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.ossreviewtoolkit.server.dao.repositories.DaoAdvisorJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAdvisorRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoEvaluatorJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoEvaluatorRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoOrtRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.EvaluatorRun
import org.ossreviewtoolkit.server.model.runs.OrtRuleViolation
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorConfiguration
import org.ossreviewtoolkit.utils.common.gibibytes

private const val TIME_STAMP_SECONDS = 1678119934L

class EvaluatorWorkerDaoTest : WordSpec({
    val advisorRunRepository = DaoAdvisorRunRepository()
    val analyzerRunRepository = DaoAnalyzerRunRepository()
    val evaluatorRunRepository = DaoEvaluatorRunRepository()
    val dao = EvaluatorWorkerDao(
        advisorJobRepository = DaoAdvisorJobRepository(),
        advisorRunRepository = advisorRunRepository,
        analyzerJobRepository = DaoAnalyzerJobRepository(),
        analyzerRunRepository = analyzerRunRepository,
        evaluatorJobRepository = DaoEvaluatorJobRepository(),
        ortRunRepository = DaoOrtRunRepository(),
        repositoryRepository = DaoRepositoryRepository(),
        evaluatorRunRepository = evaluatorRunRepository
    )
    lateinit var fixtures: Fixtures

    extension(
        DatabaseTestExtension {
            fixtures = Fixtures()
        }
    )

    "getEvaluatorJob" should {
        "return job if job does exist" {
            val job = dao.getEvaluatorJob(fixtures.evaluatorJob.id)

            job shouldBe fixtures.evaluatorJob
        }

        "return null if job does not exist" {
            dao.getEvaluatorJob(-1L) shouldBe null
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

    "getAnalyzerRunForEvaluatorJob" should {
        "return an analyzer run" {
            val createdAnalyzerRun = analyzerRunRepository.create(
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

            val requestedAnalyzerRun = dao.getAnalyzerRunForEvaluatorJob(fixtures.evaluatorJob)

            requestedAnalyzerRun shouldBe createdAnalyzerRun
        }

        "return null if run does not exist" {
            dao.getAnalyzerRunForEvaluatorJob(fixtures.evaluatorJob.copy(ortRunId = -1L)) shouldBe null
        }
    }

    "getAdvisorRunForEvaluatorJob" should {
        "return an advisor run" {
            val createdAdvisorRun = advisorRunRepository.create(
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

            val requestedAdvisorRun = dao.getAdvisorRunForEvaluatorJob(fixtures.evaluatorJob)

            requestedAdvisorRun shouldBe createdAdvisorRun
        }

        "return null if run does not exist" {
            dao.getAdvisorRunForEvaluatorJob(fixtures.evaluatorJob.copy(ortRunId = -1L)) shouldBe null
        }
    }

    "storeEvaluatorRun" should {
        "store an evaluator run in the database" {
            val evaluatorRun = EvaluatorRun(
                id = 1L,
                evaluatorJobId = fixtures.evaluatorJob.id,
                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                violations = listOf(
                    OrtRuleViolation(
                        rule = "rule",
                        fixtures.identifier,
                        license = "license",
                        licenseSource = "license source",
                        severity = "ERROR",
                        message = "the rule is violated",
                        howToFix = "how to fix info"
                    )
                )
            )

            dao.storeEvaluatorRun(evaluatorRun)

            evaluatorRunRepository.getByJobId(fixtures.evaluatorJob.id) shouldBe evaluatorRun
        }
    }
})
