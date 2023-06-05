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

package org.ossreviewtoolkit.server.workers.analyzer

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.server.dao.repositories.DaoAdvisorRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerRunRepository
import org.ossreviewtoolkit.server.dao.tables.AdvisorJobDao
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun
import org.ossreviewtoolkit.server.workers.advisor.AdvisorWorkerDao
import org.ossreviewtoolkit.utils.common.gibibytes

class AdvisorWorkerDaoTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var analyzerRunRepository: DaoAnalyzerRunRepository
    lateinit var dao: AdvisorWorkerDao
    lateinit var fixtures: Fixtures

    beforeEach {
        analyzerRunRepository = DaoAnalyzerRunRepository(dbExtension.db)
        dao = AdvisorWorkerDao(
            dbExtension.fixtures.advisorJobRepository,
            DaoAdvisorRunRepository(dbExtension.db),
            dbExtension.fixtures.analyzerJobRepository,
            analyzerRunRepository,
            dbExtension.fixtures.ortRunRepository
        )
        fixtures = dbExtension.fixtures
    }

    "getAnalyzerRunByJobId" should {
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

            val requestedAnalyzerRun = dao.getAnalyzerRunForAdvisorJob(fixtures.advisorJob)

            requestedAnalyzerRun shouldBe createdAnalyzerRun
        }

        "return null if run does not exist" {
            dao.getAnalyzerRunForAdvisorJob(fixtures.advisorJob.copy(ortRunId = -1L)) shouldBe null
        }
    }

    "getAnalyzerJob" should {
        "return job if job does exist" {
            val job = dao.getAdvisorJob(fixtures.advisorJob.id)

            job shouldBe fixtures.advisorJob
        }

        "return null if job does not exist" {
            dao.getAdvisorJob(-1L) shouldBe null
        }
    }

    "storeAnalyzerRun" should {
        "store the run correctly" {
            // TODO: Use an advisor result with defects and vulnerabilities.
            val run = AdvisorRun(
                id = 1,
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
                config = AdvisorConfiguration(
                    githubDefectsConfiguration = null,
                    nexusIqConfiguration = null,
                    osvConfiguration = null,
                    vulnerableCodeConfiguration = null,
                    options = emptyMap()
                ),
                advisorRecords = emptyMap()
            )

            dao.storeAdvisorRun(run)

            val storedRun = transaction {
                AdvisorJobDao[fixtures.advisorJob.id].advisorRun?.mapToModel()
            }

            storedRun.shouldNotBeNull()
            with(storedRun) {
                advisorJobId shouldBe fixtures.advisorJob.id
                environment shouldBe run.environment
                config shouldBe run.config
                advisorRecords should beEmpty()
            }
        }
    }
})
